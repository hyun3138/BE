package com.example.Loark.Controller;

import com.example.Loark.DTO.BlockRequest;
import com.example.Loark.DTO.FriendRequest;
import com.example.Loark.DTO.FriendResponse;
import com.example.Loark.Entity.Character;
import com.example.Loark.Entity.Friend_Memo;
import com.example.Loark.Entity.User;
import com.example.Loark.Repository.CharacterRepository;
import com.example.Loark.Service.FriendService;
import com.example.Loark.Entity.Friend;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.domain.Sort;

import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class FriendController {
    private final FriendService friendService;
    private final CharacterRepository characterRepository; // 의존성 추가

    @PostMapping("/friends/request")
    public ResponseEntity<String> sendRequest(
            @RequestBody FriendRequest req,
            @AuthenticationPrincipal User me
    ) {
        if (me == null) return ResponseEntity.status(401).body("인증 필요");
        friendService.sendRequest(me.getUserId(), req.getTargetMainCharacter());
        return ResponseEntity.ok("친구 요청 완료");
    }

    @PostMapping("/friends/requests/{friendId}/accept")
    public ResponseEntity<String> accept(
            @PathVariable Long friendId,
            @AuthenticationPrincipal User me
    ) {
        if (me == null) return ResponseEntity.status(401).body("인증 필요");
        friendService.accept(me.getUserId(), friendId);
        return ResponseEntity.ok("친구 요청 수락");
    }

    @PostMapping("/friends/requests/{friendId}/decline")
    public ResponseEntity<String> decline(
            @PathVariable Long friendId,
            @AuthenticationPrincipal User me
    ) {
        if (me == null) return ResponseEntity.status(401).body("인증 필요");
        friendService.decline(me.getUserId(), friendId);
        return ResponseEntity.ok("친구 요청 거절");
    }

    @DeleteMapping("/friends/{friendId}")
    public ResponseEntity<String> delete(
            @PathVariable Long friendId,
            @AuthenticationPrincipal User me
    ) {
        if (me == null) return ResponseEntity.status(401).body("인증 필요");
        friendService.delete(me.getUserId(), friendId);
        return ResponseEntity.ok("친구 삭제 완료");
    }

    @PostMapping("/friends/requests/{friendId}/cancel")
    public ResponseEntity<String> cancel(
            @PathVariable Long friendId,
            @AuthenticationPrincipal User me
    ) {
        if (me == null) return ResponseEntity.status(401).body("인증 필요");
        friendService.cancel(me.getUserId(), friendId);
        return ResponseEntity.ok("친구 요청 취소 완료");
    }

    @GetMapping("/friends")
    public ResponseEntity<Page<FriendResponse>> list(
            @RequestParam String status,
            @RequestParam(required = false) String query,
            @AuthenticationPrincipal User me,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable
    ) {
        if (me == null) return ResponseEntity.status(401).build();

        Pageable safePageable = remapSort(pageable);
        Page<Friend> page = friendService.list(me.getUserId(), status, query, safePageable);
        Page<FriendResponse> dtoPage = page.map(f -> toDto(f, me.getUserId()));
        return ResponseEntity.ok(dtoPage);
    }

    @PostMapping("/blocks")
    public ResponseEntity<String> block(
            @RequestBody BlockRequest req,
            @AuthenticationPrincipal User me
    ) {
        if (me == null) return ResponseEntity.status(401).body("인증 필요");
        friendService.block(me.getUserId(), req.getBlockedUserId());
        return ResponseEntity.ok("차단 완료");
    }

    @DeleteMapping("/blocks/{blockedUserId}")
    public ResponseEntity<String> unblock(
            @PathVariable Long blockedUserId,
            @AuthenticationPrincipal User me
    ) {
        if (me == null) return ResponseEntity.status(401).body("인증 필요");
        friendService.unblock(me.getUserId(), blockedUserId);
        return ResponseEntity.ok("차단 해제 완료");
    }
    // ✅ 메모 수정 (내 메모만 갱신)
    @PatchMapping("/friends/{friendId}/memo")
    public ResponseEntity<String> updateMemo(
            @PathVariable Long friendId,
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal User me
    ) {
        if (me == null) return ResponseEntity.status(401).body("인증 필요");
        String memo = body.getOrDefault("memo", null);
        friendService.updateMemo(me.getUserId(), friendId, memo);
        return ResponseEntity.ok("메모가 저장되었습니다.");
    }

    // ✅ 메모 삭제 (내 메모만 삭제)
    @DeleteMapping("/friends/{friendId}/memo")
    public ResponseEntity<String> clearMemo(
            @PathVariable Long friendId,
            @AuthenticationPrincipal User me
    ) {
        if (me == null) return ResponseEntity.status(401).body("인증 필요");
        friendService.clearMemo(me.getUserId(), friendId);
        return ResponseEntity.ok("메모가 삭제되었습니다.");
    }

    // ✅ 목록 응답에 “내 메모”를 실어 주기 위한 매핑 보정
    private FriendResponse toDto(Friend f, Long me) {
        boolean iAmRequester = f.getRequester().getUserId().equals(me);
        User other = iAmRequester ? f.getTarget() : f.getRequester();

        // 내 메모만 조회해서 DTO에 채움
        Friend_Memo myMemo = friendService.findMyMemo(me, f.getFriendId());

        // 상대방의 메인 캐릭터 이름과 직업 조회
        String mainCharacterName = other.getMainCharacter();
        String characterClass = null;
        if (mainCharacterName != null && !mainCharacterName.isBlank()) {
            Optional<Character> characterOpt = characterRepository.findByName(mainCharacterName);
            if (characterOpt.isPresent()) {
                characterClass = characterOpt.get().getClazz();
            }
        }

        return FriendResponse.builder()
                .friendId(f.getFriendId())
                .otherUserId(other.getUserId())
                .otherNickname(other.getDisplayName())
                .status(f.getStatus())
                .createdAt(f.getCreatedAt())
                .respondedAt(f.getRespondedAt())
                .friendMemo(myMemo != null ? myMemo.getMemoText() : null)
                .friendMemoUpdatedAt(myMemo != null ? myMemo.getUpdatedAt().atOffset(java.time.ZoneOffset.systemDefault().getRules().getOffset(myMemo.getUpdatedAt())) : null)
                .mainCharacterName(mainCharacterName)
                .characterClass(characterClass)
                .build();
    }

    // 허용 정렬 키 매핑 (중첩 경로는 displayName으로 교체)
    private static final Map<String, String> SORT_MAP = Map.of(
            "createdAt", "createdAt",
            "id",        "friendId",
            "requesterName", "requester.displayName", // 🔁
            "targetName",    "target.displayName"     // 🔁
    );

    private Pageable remapSort(Pageable pageable) {
        if (pageable.getSort().isUnsorted()) {
            return PageRequest.of(
                    pageable.getPageNumber(),
                    pageable.getPageSize(),
                    Sort.by(Sort.Order.desc("createdAt"))
            );
        }
        Sort mapped = Sort.by(
                pageable.getSort().stream()
                        .map(o -> new Sort.Order(
                                o.getDirection(),
                                SORT_MAP.getOrDefault(o.getProperty(), "createdAt")
                        ))
                        .toList()
        );
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), mapped);
    }

}
