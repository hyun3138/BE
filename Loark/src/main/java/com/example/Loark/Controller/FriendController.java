package com.example.Loark.Controller;

import com.example.Loark.DTO.BlockRequest;
import com.example.Loark.DTO.FriendRequest;
import com.example.Loark.DTO.FriendResponse;
import com.example.Loark.Service.FriendService;
import com.example.Loark.Entity.Friend;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import java.util.Map;
import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class FriendController {

    private final FriendService friendService;

    // 친구 요청
    @PostMapping("/friends/request")
    public ResponseEntity<String> sendRequest(
            @RequestBody FriendRequest req,
            @RequestHeader("X-User-Id") Long me
    ) {
        friendService.sendRequest(me, req.getTargetUserId());
        return ResponseEntity.ok("친구 요청 완료");
    }

    // 수락
    @PostMapping("/friends/requests/{friendId}/accept")
    public ResponseEntity<String> accept(
            @PathVariable Long friendId,
            @RequestHeader("X-User-Id") Long me
    ) {
        friendService.accept(me, friendId);
        return ResponseEntity.ok("친구 요청 수락");
    }

    // 거절
    @PostMapping("/friends/requests/{friendId}/decline")
    public ResponseEntity<String> decline(
            @PathVariable Long friendId,
            @RequestHeader("X-User-Id") Long me
    ) {
        friendService.decline(me, friendId);
        return ResponseEntity.ok("친구 요청 거절");
    }

    // 삭제
    @DeleteMapping("/friends/{friendId}")
    public ResponseEntity<String> delete(
            @PathVariable Long friendId,
            @RequestHeader("X-User-Id") Long me
    ) {
        friendService.delete(me, friendId);
        return ResponseEntity.ok("친구 삭제 완료");
    }

    // 목록/검색
    @GetMapping("/friends")
    public ResponseEntity<Page<FriendResponse>> list(
            @RequestParam String status,
            @RequestParam(required = false) String query,
            @RequestHeader("X-User-Id") Long me,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable
    ) {
        Pageable safePageable = remapSort(pageable);

        Page<Friend> page = friendService.list(me, status, query, safePageable);
        Page<FriendResponse> dtoPage = page.map(f -> toDto(f, me));
        return ResponseEntity.ok(dtoPage);
    }

    // 차단
    @PostMapping("/blocks")
    public ResponseEntity<String> block(
            @RequestBody BlockRequest req,
            @RequestHeader("X-User-Id") Long me
    ) {
        friendService.block(me, req.getBlockedUserId());
        return ResponseEntity.ok("차단 완료");
    }

    // 차단 해제
    @DeleteMapping("/blocks/{blockedUserId}")
    public ResponseEntity<String> unblock(
            @PathVariable Long blockedUserId,
            @RequestHeader("X-User-Id") Long me
    ) {
        friendService.unblock(me, blockedUserId);
        return ResponseEntity.ok("차단 해제 완료");
    }

    private FriendResponse toDto(Friend f, Long me) {
        boolean iAmRequester = f.getRequester().getUserId().equals(me);
        var other = iAmRequester ? f.getTarget() : f.getRequester();
        return FriendResponse.builder()
                .friendId(f.getFriendId())
                .otherUserId(other.getUserId())
                .otherNickname(other.getUserNickname())
                .status(f.getStatus())
                .createdAt(f.getCreatedAt())
                .respondedAt(f.getRespondedAt())
                .build();
    }

    private static final Map<String, String> SORT_MAP = Map.of(
            "createdAt", "createdAt",          // Friend.createdAt
            "id",        "friendId",           // Friend.friendId
            "requesterName", "requester.userNickname", // 연관 경로 정렬 (요청 보낸 사람 닉네임)
            "targetName",    "target.userNickname"     // 연관 경로 정렬 (상대 닉네임)
    );

    private Pageable remapSort(Pageable pageable) {
        // 정렬이 없으면 기본(createdAt DESC)
        if (pageable.getSort().isUnsorted()) {
            return PageRequest.of(
                    pageable.getPageNumber(),
                    pageable.getPageSize(),
                    Sort.by(Sort.Order.desc("createdAt"))
            );
        }

        // 허용되지 않은 키 → createdAt으로 폴백
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
