package com.example.Loark.Controller;

import com.example.Loark.DTO.FactGateMetricsDto;
import com.example.Loark.Entity.CharacterSpec;
import com.example.Loark.Entity.User;
import com.example.Loark.Repository.CharacterRepository;
import com.example.Loark.Repository.UserRepository;
import com.example.Loark.Service.CharacterService;
import com.example.Loark.Service.LostarkApiClient;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.example.Loark.Service.CharacterMapper.toDto;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/characters")
public class CharacterController {
    private final CharacterRepository characterRepo;
    private final UserRepository userRepository;
    private final CharacterService characterService;
    private final LostarkApiClient lostarkApi;

    @GetMapping("/{characterName}/spec/latest")
    public ResponseEntity<CharacterSpec> getLatestCharacterSpec(@PathVariable String characterName) {
        Optional<CharacterSpec> specOpt = characterService.getLatestCharacterSpec(characterName);
        return specOpt.map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/{characterName}/spec")
    public ResponseEntity<CharacterSpec> getCharacterSpecByDate(
            @PathVariable String characterName,
            @RequestParam("date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        Optional<CharacterSpec> specOpt = characterService.getCharacterSpecByDate(characterName, date);
        return specOpt.map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * 특정 캐릭터의 모든 전투 기록을 조회합니다.
     */
    @GetMapping("/{characterName}/combat-records")
    public ResponseEntity<?> getCombatRecordsForCharacter(@PathVariable String characterName,
                                                          @AuthenticationPrincipal User me) {
        if (me == null) {
            return ResponseEntity.status(401).body("인증이 필요합니다.");
        }

        try {
            List<FactGateMetricsDto> combatRecords = characterService.getCharacterCombatRecords(characterName, me);
            return ResponseEntity.ok(combatRecords);
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * 특정 전투 기록을 삭제합니다.
     */
    @DeleteMapping("/combat-records/{recordId}")
    public ResponseEntity<?> deleteCombatRecord(@PathVariable Long recordId,
                                                @AuthenticationPrincipal User me) {
        if (me == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "인증이 필요합니다."));
        }

        try {
            characterService.deleteCharacterCombatRecord(recordId, me);
            return ResponseEntity.ok().body(Map.of("message", "전투 기록이 성공적으로 삭제되었습니다."));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "기록 삭제 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }

    @PostMapping("/{characterName}/spec")
    public ResponseEntity<?> saveCharacterSpec(@AuthenticationPrincipal User me,
                                               @PathVariable String characterName) {
        if (me == null) {
            return ResponseEntity.status(401).body("인증이 필요합니다.");
        }
        try {
            characterService.saveCharacterSpec(me, characterName);
            return ResponseEntity.ok(characterName + " 캐릭터의 스펙 정보가 저장되었습니다.");
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(500).body("스펙 정보 처리 중 오류가 발생했습니다: " + e.getMessage());
        }
    }

    @PostMapping("/save-main")
    public ResponseEntity<?> saveMain(@AuthenticationPrincipal User me,
                                      @RequestParam(required = false) String apiKeyOpt) {

        if (me == null) return ResponseEntity.status(401).body("인증 필요");
        String apiKey = (apiKeyOpt != null) ? apiKeyOpt : me.getUserApiKey();
        if (apiKey == null || apiKey.isBlank()) return ResponseEntity.badRequest().body("API Key가 필요합니다.");
        if (me.getMainCharacter() == null || me.getMainCharacter().isBlank())
            return ResponseEntity.badRequest().body("대표 캐릭터가 설정되어 있지 않습니다. 스토브 인증을 먼저 완료하세요.");

        if (!lostarkApi.existsCharacter(apiKey, me.getMainCharacter()))
            return ResponseEntity.badRequest().body("대표 캐릭터를 찾을 수 없습니다. 닉네임/API Key를 확인하세요.");

        var siblings = lostarkApi.fetchSiblings(apiKey, me.getMainCharacter());
        String main = me.getMainCharacter();

        int inserted = 0, duplicated = 0, error = 0;
        if (siblings == null || siblings.isEmpty()) {
            try {
                characterService.insertOnlyFromProfileWithArkPassive(me, main, true);
                inserted++;
            } catch (IllegalStateException dup) {
                duplicated++;
            } catch (Exception e) {
                error++;
            }
            return ResponseEntity.ok("저장 완료: 신규 " + inserted + "명, 중복 " + duplicated + "명, 오류 " + error + "명 (대표: " + main + ")");
        }

        for (var sib : siblings) {
            String name = sib.getCharacterName();
            boolean setMain = main.equalsIgnoreCase(name);
            try {
                characterService.insertOnlyFromProfileWithArkPassive(me, name, setMain);
                inserted++;
            } catch (IllegalStateException dup) {
                duplicated++;
            } catch (Exception e) {
                error++;
            }
        }

        return ResponseEntity.ok("원정대 저장 완료: 신규 " + inserted + "명, 중복 " + duplicated + "명, 오류 " + error + "명 (대표: " + main + ")");
    }

    @PostMapping("/toggle-main")
    @Transactional
    public ResponseEntity<?> toggleMain(@AuthenticationPrincipal User me,
                                        @RequestParam String name) {
        if (me == null) return ResponseEntity.status(401).body("인증 필요");
        var target = characterRepo.findByUserAndName(me, name)
                .orElse(null);
        if (target == null) return ResponseEntity.badRequest().body("해당 캐릭터가 저장되어 있지 않습니다.");

        characterRepo.findByUserAndMainTrue(me).ifPresent(prev -> {
            if (!prev.getName().equalsIgnoreCase(name)) {
                prev.setMain(false);
                characterRepo.save(prev);
            }
        });
        target.setMain(true);
        userRepository.findById(me.getUserId()).ifPresent(u -> {
            u.setMainCharacter(name);
            userRepository.save(u);
        });
        characterRepo.save(target);
        return ResponseEntity.ok("대표 캐릭터 변경 완료: " + name);
    }

    @GetMapping("/list")
    public ResponseEntity<?> getMyCharacters(@AuthenticationPrincipal User me) {
        if (me == null) return ResponseEntity.status(401).body("인증 필요");

        var list = characterRepo.findAllByUserOrderByMainDescUpdatedAtDesc(me)
                .stream()
                .map(toDto)
                .toList();

        return ResponseEntity.ok(list);
    }

    @PostMapping("/refresh")
    @Transactional
    public ResponseEntity<?> refreshAll(@AuthenticationPrincipal User me) {
        if (me == null) return ResponseEntity.status(401).body("인증 필요");

        var sum = characterService.refreshAllHigher(me);

        var body = java.util.Map.of(
                "updated",  sum.getUpdated(),
                "skipped",  sum.getSkipped(),
                "errors",   sum.getError()
        );
        return ResponseEntity.ok(body);
    }
}
