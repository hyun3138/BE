package com.example.Loark.Controller;

import com.example.Loark.DTO.ChangeMainRequest;
import com.example.Loark.Entity.Character;
import com.example.Loark.Entity.User;
import com.example.Loark.Repository.CharacterRepository;
import com.example.Loark.Repository.UserRepository;
import com.example.Loark.Service.CharacterService;
import com.example.Loark.Service.LostarkApiClient;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import static com.example.Loark.Service.CharacterMapper.toDto;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/characters")
public class CharacterController {
    private final CharacterRepository characterRepo;
    private final UserRepository userRepository;
    private final CharacterService characterService;
    private final LostarkApiClient lostarkApi;

    /** 대표 캐릭터 저장(본인캐릭터 인증 버튼) */
    @PostMapping("/save-main")
    @Transactional
    public ResponseEntity<?> saveMain(@AuthenticationPrincipal User me,
                                      @RequestParam(required = false) String apiKeyOpt) {
        if (me == null) return ResponseEntity.status(401).body("인증 필요");
        String apiKey = apiKeyOpt != null ? apiKeyOpt : me.getUserApiKey();
        if (apiKey == null || apiKey.isBlank()) return ResponseEntity.badRequest().body("API Key가 필요합니다.");
        if (me.getMainCharacter() == null || me.getMainCharacter().isBlank())
            return ResponseEntity.badRequest().body("대표 캐릭터가 설정되어 있지 않습니다. 스토브 인증을 먼저 완료하세요.");

        // 존재 검증(선택) + 저장
        if (!lostarkApi.existsCharacter(apiKey, me.getMainCharacter()))
            return ResponseEntity.badRequest().body("대표 캐릭터를 찾을 수 없습니다. 닉네임/API Key를 확인하세요.");

        var saved = characterService.upsertFromProfile(me, me.getMainCharacter(), true);

        // 기존 main 해제 로직은 service 내부 또는 여기서 토글
        characterRepo.findByUserAndMainTrue(me).ifPresent(prev -> {
            if (!prev.getName().equalsIgnoreCase(saved.getName())) {
                prev.setMain(false);
                characterRepo.save(prev);
            }
        });
        return ResponseEntity.ok("대표 캐릭터 저장 완료: " + saved.getName());
    }

    /** 다른 캐릭터 추가(같은 원정대 검증) */
    @PostMapping("/save-other")
    @Transactional
    public ResponseEntity<?> saveOther(@AuthenticationPrincipal User me,
                                       @RequestParam String name,
                                       @RequestParam(required = false) String apiKeyOpt) {
        if (me == null) return ResponseEntity.status(401).body("인증 필요");
        String apiKey = apiKeyOpt != null ? apiKeyOpt : me.getUserApiKey();
        if (apiKey == null || apiKey.isBlank()) return ResponseEntity.badRequest().body("API Key가 필요합니다.");
        if (me.getMainCharacter() == null || me.getMainCharacter().isBlank())
            return ResponseEntity.badRequest().body("대표 캐릭터가 설정되어 있지 않습니다. 먼저 본인캐릭터 인증을 완료하세요.");

        // 같은 원정대인지 siblings로 검증
        boolean same = lostarkApi.areSameExpedition(apiKey, me.getMainCharacter(), name);
        if (!same) return ResponseEntity.badRequest().body("같은 원정대 캐릭터가 아닙니다.");

        var saved = characterService.upsertFromProfile(me, name, false);
        return ResponseEntity.ok("캐릭터 추가 완료: " + saved.getName());
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

    // 로그인 한 유저의 대표 캐릭터 기준으로
    @GetMapping("/siblings")
    public ResponseEntity<?> mySiblings(@AuthenticationPrincipal User me) {
        if (me == null) return ResponseEntity.status(401).body("인증 필요");
        if (me.getUserApiKey() == null || me.getUserApiKey().isBlank())
            return ResponseEntity.badRequest().body("API Key가 필요합니다.");
        if (me.getMainCharacter() == null || me.getMainCharacter().isBlank())
            return ResponseEntity.badRequest().body("대표 캐릭터가 설정되어 있지 않습니다.");

        var list = lostarkApi.fetchSiblings(me.getUserApiKey(), me.getMainCharacter());
        return ResponseEntity.ok(list); // 그대로 프록시
    }
    
    // 임의의 캐릭터 명으로 조회
    @GetMapping("/{characterName}/siblings")
    public ResponseEntity<?> siblingsByName(@AuthenticationPrincipal User me,
                                            @PathVariable String characterName,
                                            @RequestParam(required = false) String apiKeyOpt) {
        if (me == null) return ResponseEntity.status(401).body("인증 필요");
        String apiKey = (apiKeyOpt != null && !apiKeyOpt.isBlank()) ? apiKeyOpt : me.getUserApiKey();
        if (apiKey == null || apiKey.isBlank())
            return ResponseEntity.badRequest().body("API Key가 필요합니다.");

        var list = lostarkApi.fetchSiblings(apiKey, characterName);
        return ResponseEntity.ok(list);
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
}
