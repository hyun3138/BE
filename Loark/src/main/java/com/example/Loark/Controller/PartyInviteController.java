package com.example.Loark.Controller;

import com.example.Loark.DTO.PartyInviteRequest;
import com.example.Loark.Entity.PartyInvite;
import com.example.Loark.Entity.User;
import com.example.Loark.Service.PartyInviteMapper;
import com.example.Loark.Service.PartyInviteService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/parties")
@RequiredArgsConstructor
public class PartyInviteController {

    private final PartyInviteService service;

    /** 초대 생성 (공대장만) */
    @PostMapping("/{partyId}/invites")
    public ResponseEntity<?> invite(@PathVariable UUID partyId,
                                    @RequestBody PartyInviteRequest req,
                                    @AuthenticationPrincipal User me) {
        if (me == null) return ResponseEntity.status(401).body("인증 필요");
        PartyInvite saved = service.create(partyId, me.getUserId(), req.getInviteeUserId());
        return ResponseEntity.ok(PartyInviteMapper.toDto(saved));
    }

    /** 초대 수락 (받은 사람만) */
    @PostMapping("/{partyId}/invites/{inviteId}/accept")
    public ResponseEntity<?> accept(@PathVariable UUID partyId,
                                    @PathVariable UUID inviteId,
                                    @AuthenticationPrincipal User me) {
        if (me == null) return ResponseEntity.status(401).body("인증 필요");
        service.accept(inviteId, me.getUserId());
        return ResponseEntity.ok("초대를 수락했습니다.");
    }

    /** 초대 거절 (받은 사람만) */
    @PostMapping("/{partyId}/invites/{inviteId}/decline")
    public ResponseEntity<?> decline(@PathVariable UUID partyId,
                                     @PathVariable UUID inviteId,
                                     @AuthenticationPrincipal User me) {
        if (me == null) return ResponseEntity.status(401).body("인증 필요");
        service.decline(inviteId, me.getUserId());
        return ResponseEntity.ok("초대를 거절했습니다.");
    }

    /** 초대 취소 (공대장만) */
    @PostMapping("/{partyId}/invites/{inviteId}/cancel")
    public ResponseEntity<?> cancel(@PathVariable UUID partyId,
                                    @PathVariable UUID inviteId,
                                    @AuthenticationPrincipal User me) {
        if (me == null) return ResponseEntity.status(401).body("인증 필요");
        service.cancel(inviteId, me.getUserId());
        return ResponseEntity.ok("초대를 취소했습니다.");
    }
}
