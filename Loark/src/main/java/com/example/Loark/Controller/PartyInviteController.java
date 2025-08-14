package com.example.Loark.Controller;

import com.example.Loark.DTO.PartyInviteRequest;
import com.example.Loark.Entity.PartyInvite;
import com.example.Loark.Entity.PartyInviteStatus;
import com.example.Loark.Entity.User;
import com.example.Loark.Repository.PartyInviteRepository;
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
    private final PartyInviteRepository invites;

    /** 초대 생성 (공대장만) */
    @PostMapping("/{partyId}/invites")
    public ResponseEntity<?> invite(@PathVariable UUID partyId,
                                    @RequestBody PartyInviteRequest req,
                                    @AuthenticationPrincipal User me) {
        if (me == null) return ResponseEntity.status(401).body("인증 필요");

        PartyInvite saved;
        if (req.getInviteeUserId() != null) {
            // 기존 방식: userId로 초대
            saved = service.create(partyId, me.getUserId(), req.getInviteeUserId());
        } else if (req.getInviteeNickname() != null && !req.getInviteeNickname().isBlank()) {
            // ✅ 신규: 대표 캐릭터 닉네임으로 초대
            saved = service.createByNickname(partyId, me.getUserId(), req.getInviteeNickname());
        } else {
            return ResponseEntity.badRequest().body("inviteeUserId 또는 inviteeNickname 중 하나는 반드시 있어야 합니다.");
        }
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

    // 내가 "받은" 초대 목록 (기본: PENDING)
    @GetMapping("/invites/received")
    public ResponseEntity<?> received(@AuthenticationPrincipal User me,
                                      @RequestParam(defaultValue = "PENDING") PartyInviteStatus status) {
        if (me == null) return ResponseEntity.status(401).body("인증 필요");
        var list = invites.findByInvitee_UserIdAndStatus(me.getUserId(), status)
                .stream().map(PartyInviteMapper::toDto).toList();
        return ResponseEntity.ok(list);
    }

    // 내가 "보낸"(신청한) 초대 목록 (기본: PENDING)
    @GetMapping("/invites/sent")
    public ResponseEntity<?> sent(@AuthenticationPrincipal User me,
                                  @RequestParam(defaultValue = "PENDING") PartyInviteStatus status) {
        if (me == null) return ResponseEntity.status(401).body("인증 필요");
        var list = invites.findByInviter_UserIdAndStatus(me.getUserId(), status)
                .stream().map(PartyInviteMapper::toDto).toList();
        return ResponseEntity.ok(list);
    }
}
