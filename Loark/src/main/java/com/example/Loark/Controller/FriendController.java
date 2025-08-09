package com.example.Loark.Controller;

import com.example.Loark.DTO.BlockRequest;
import com.example.Loark.DTO.FriendRequest;
import com.example.Loark.DTO.FriendResponse;
import com.example.Loark.Entity.User;
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

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class FriendController {
    private final FriendService friendService;

    @PostMapping("/friends/request")
    public ResponseEntity<String> sendRequest(
            @RequestBody FriendRequest req,
            @AuthenticationPrincipal User me
    ) {
        // (선택) 방어 코드
        if (me == null) return ResponseEntity.status(401).body("인증 필요");
        friendService.sendRequest(me.getUserId(), req.getTargetUserId());
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

    private FriendResponse toDto(Friend f, Long me) {
        boolean iAmRequester = f.getRequester().getUserId().equals(me);
        var other = iAmRequester ? f.getTarget() : f.getRequester();
        return FriendResponse.builder()
                .friendId(f.getFriendId())
                .otherUserId(other.getUserId())
                .otherNickname(other.getDisplayName()) // 🔁 displayName으로 교체
                .status(f.getStatus())
                .createdAt(f.getCreatedAt())
                .respondedAt(f.getRespondedAt())
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
