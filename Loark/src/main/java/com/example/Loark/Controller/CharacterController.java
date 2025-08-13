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

        // 1) 대표 캐릭터 실존 확인은 유지
        if (!lostarkApi.existsCharacter(apiKey, me.getMainCharacter()))
            return ResponseEntity.badRequest().body("대표 캐릭터를 찾을 수 없습니다. 닉네임/API Key를 확인하세요.");

        // 2) 원정대 목록 불러오기
        var siblings = lostarkApi.fetchSiblings(apiKey, me.getMainCharacter());
        // fetchSiblings가 비면 최소 대표만 저장하도록 fallback
        if (siblings == null || siblings.isEmpty()) {
            var saved = characterService.upsertFromProfileWithArkPassive(me, me.getMainCharacter(), true);
            // 기존 main 토글 유지
            characterRepo.findByUserAndMainTrue(me).ifPresent(prev -> {
                if (!prev.getName().equalsIgnoreCase(saved.getName())) {
                    prev.setMain(false);
                    characterRepo.save(prev);
                }
            });
            return ResponseEntity.ok("대표 캐릭터만 저장 완료: " + saved.getName());
        }

        // 3) 원정대 전체 저장 (프로필 + 아크패시브)
        int savedCount = 0;
        String main = me.getMainCharacter();
        for (var sib : siblings) {
            String name = sib.getCharacterName(); // DTO: LoaSiblings.characterName
            boolean isMain = main.equalsIgnoreCase(name);
            characterService.upsertFromProfileWithArkPassive(me, name, isMain);
            savedCount++;
        }

        // 대표 토글 재확인(방어)
        characterRepo.findByUserAndMainTrue(me).ifPresent(prev -> {
            if (!prev.getName().equalsIgnoreCase(main)) {
                prev.setMain(false);
                characterRepo.save(prev);
            }
        });
        // 대표 이름이 저장되지 않았다면 대표를 강제 토글
        characterRepo.findByUserAndName(me, main).ifPresentOrElse(ch -> {
            if (!ch.isMain()) {
                characterRepo.findByUserAndMainTrue(me).ifPresent(prev -> { prev.setMain(false); characterRepo.save(prev); });
                ch.setMain(true);
                characterRepo.save(ch);
            }
        }, () -> {
            // 혹시 siblings 응답에 대표가 누락되면 직접 저장
            characterService.upsertFromProfileWithArkPassive(me, main, true);
        });

        return ResponseEntity.ok("원정대 전체 저장 완료 (" + savedCount + "명). 대표: " + main);
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
    
    // 사용자의 모든 캐릭터 정보 간단하게 반환
}
