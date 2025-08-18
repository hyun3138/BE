package com.example.Loark.Controller;

import com.example.Loark.DTO.ChangeMainRequest;
import com.example.Loark.Entity.Character;
import com.example.Loark.Entity.CharacterSpec;
import com.example.Loark.Entity.User;
import com.example.Loark.Repository.CharacterRepository;
import com.example.Loark.Repository.UserRepository;
import com.example.Loark.Service.CharacterService;
import com.example.Loark.Service.LostarkApiClient;
import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
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
        } catch (JsonProcessingException e) {
            return ResponseEntity.status(500).body("스펙 정보 처리 중 오류가 발생했습니다.");
        } catch (Exception e) {
            return ResponseEntity.status(500).body("서버 내부 오류가 발생했습니다.");
        }
    }

    /** ✅ 대표 캐릭터 저장(= 인증 버튼): 이제 원정대 전체 저장 + arkpassive 포함 */
    @PostMapping("/save-main")
    @Transactional
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

        int inserted = 0, duplicated = 0;
        if (siblings == null || siblings.isEmpty()) {
            // 대표만 저장 시도(중복이면 예외 → 잡아서 중복 카운트)
            try {
                characterService.insertOnlyFromProfileWithArkPassive(me, main, true);
                inserted++;
            } catch (IllegalStateException dup) {
                duplicated++;
            }
            return ResponseEntity.ok("저장 완료: 신규 " + inserted + "명, 중복 " + duplicated + "명 (대표: " + main + ")");
        }

        for (var sib : siblings) {
            String name = sib.getCharacterName();
            boolean setMain = main.equalsIgnoreCase(name);
            try {
                characterService.insertOnlyFromProfileWithArkPassive(me, name, setMain);
                inserted++;
            } catch (IllegalStateException dup) {
                duplicated++;
            }
        }

        // 대표 보정(이미 DB에 있었을 수도 있으니 main 토글만 보장)
        characterRepo.findByUserAndName(me, main).ifPresent(ch -> {
            if (!ch.isMain()) {
                characterRepo.findByUserAndMainTrue(me).ifPresent(prev -> { prev.setMain(false); characterRepo.save(prev); });
                ch.setMain(true);
                characterRepo.save(ch);
            }
        });

        return ResponseEntity.ok("원정대 저장 완료: 신규 " + inserted + "명, 중복 " + duplicated + "명 (대표: " + main + ")");
    }


    /** 대표 토글만 따로 필요하면(선택) */
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
            u.setMainCharacter(name); // User.mainCharacter 동기화
            userRepository.save(u);
        });
        characterRepo.save(target);
        return ResponseEntity.ok("대표 캐릭터 변경 완료: " + name);
    }

    /** ✅ 현재 로그인 유저의 캐릭터 전부 반환 */
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

        // DB에 '이미 저장된' 내 캐릭터들만 대상으로,
        // 프로필 조회 후 상향(아이템레벨/전투력)일 때만 업데이트
        var sum = characterService.refreshAllHigher(me);

        // 요약 응답
        var body = java.util.Map.of(
                "updated",  sum.getUpdated(),   // 상향으로 실제 갱신된 수
                "skipped",  sum.getSkipped(),   // 하향/동일이라 스킵
                "errors",   sum.getError()      // API 실패 등
        );
        return ResponseEntity.ok(body);
    }

}
