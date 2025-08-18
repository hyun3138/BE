package com.example.Loark.Controller;

import com.example.Loark.DTO.PartyCreateRequest;
import com.example.Loark.DTO.PartyResponse;
import com.example.Loark.Entity.User;
import com.example.Loark.Service.PartyMapper;
import com.example.Loark.Service.PartyService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.domain.Sort;

import java.util.UUID;

@RestController
@RequestMapping("/api/parties")
@RequiredArgsConstructor
public class PartyController {
    private final PartyService partyService;

    @Data
    static class SubpartySwapRequest {
        private Long member1UserId;
        private Long member2UserId;
    }

    /** 공대 생성 (공대장 = 로그인 유저) */
    @PostMapping
    public ResponseEntity<?> create(@AuthenticationPrincipal User me,
                                    @RequestBody PartyCreateRequest req) {
        var saved = partyService.create(me, req.getName(), "", req.getVisibility());
        return ResponseEntity.ok(PartyMapper.toDto(saved));
    }

    /** 공대 가입 */
    @PostMapping("/{partyId}/join")
    public ResponseEntity<Void> join(@PathVariable UUID partyId, @AuthenticationPrincipal User me) {
        if (me == null) {
            return ResponseEntity.status(401).build();
        }
        partyService.join(partyId, me);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/joined")
    public ResponseEntity<?> joined(@AuthenticationPrincipal User me) {
        if (me == null) return ResponseEntity.status(401).body("인증 필요");
        var list = partyService.listJoined(me.getUserId()).stream()
                .map(PartyMapper::toDto)
                .toList();
        return ResponseEntity.ok(list);
    }

    @DeleteMapping("/{partyId}")
    public ResponseEntity<Void> delete(@PathVariable UUID partyId,
                                    @AuthenticationPrincipal User me) {
        if (me == null) return ResponseEntity.status(401).build();
        partyService.deleteOwned(partyId, me);
        return ResponseEntity.noContent().build();
    }
    @PatchMapping("/{partyId}/owner")
    public ResponseEntity<?> transferOwner(@PathVariable UUID partyId,
                                           @RequestParam Long newOwnerUserId,
                                           @AuthenticationPrincipal User me) {
        if (me == null) return ResponseEntity.status(401).body("인증 필요");
        partyService.transferOwner(partyId, me.getUserId(), newOwnerUserId);
        return ResponseEntity.ok("공대장 권한을 위임했습니다.");
    }

    /** 공대장이 멤버의 서브파티를 수동으로 교체 */
    @PutMapping("/{partyId}/swap-subparty")
    public ResponseEntity<?> swapSubparty(@PathVariable UUID partyId,
                                          @AuthenticationPrincipal User me,
                                          @RequestBody SubpartySwapRequest req) {
        if (me == null) return ResponseEntity.status(401).body("인증 필요");
        partyService.changeSubparty(partyId, me.getUserId(), req.getMember1UserId(), req.getMember2UserId());
        return ResponseEntity.ok("멤버의 서브파티를 교체했습니다.");
    }

    /** ✅ 전체 공개 공대 목록 (비로그인 허용) */
    @GetMapping("/public")
    public ResponseEntity<?> listPublic(
            @RequestParam(required = false) String q,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable
    ) {
        Page<PartyResponse> page = partyService.listPublic(q, pageable)
                .map(PartyMapper::toDto);
        return ResponseEntity.ok(page);
    }
}
