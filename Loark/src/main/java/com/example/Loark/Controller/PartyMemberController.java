package com.example.Loark.Controller;

import com.example.Loark.DTO.PartyMemberResponse;
import com.example.Loark.Entity.Party;
import com.example.Loark.Entity.PartyMember;
import com.example.Loark.Entity.User;
import com.example.Loark.Repository.PartyRepository;
import com.example.Loark.Service.PartyMemberMapper;
import com.example.Loark.Service.PartyMemberService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/parties/{partyId}/members")
@RequiredArgsConstructor
public class PartyMemberController {

    private final PartyMemberService service;
    private final PartyRepository parties;

    @Data
    static class AddMemberRequest {
        private Long userId;
        private String nickname;
    }

    /** 멤버 목록 조회 */
    @GetMapping
    public ResponseEntity<?> list(@PathVariable UUID partyId) {
        Party party = parties.findById(partyId)
                .orElseThrow(() -> new IllegalArgumentException("공대를 찾을 수 없습니다."));
        List<PartyMember> all = service.list(partyId);
        var dto = all.stream().map(m -> PartyMemberMapper.toDto(m, party)).toList();
        return ResponseEntity.ok(dto);
    }

    /** 멤버 즉시 추가 (공대장만) */
    @PostMapping
    public ResponseEntity<?> addMember(@PathVariable UUID partyId,
                                       @AuthenticationPrincipal User me,
                                       @RequestBody AddMemberRequest req) {
        if (me == null) {
            return ResponseEntity.status(401).body("인증 필요");
        }

        if (req.getUserId() != null) {
            service.addMemberById(partyId, me.getUserId(), req.getUserId());
        } else if (req.getNickname() != null && !req.getNickname().isBlank()) {
            service.addMemberByNickname(partyId, me.getUserId(), req.getNickname());
        } else {
            return ResponseEntity.badRequest().body("추가할 멤버의 ID 또는 닉네임을 입력해주세요.");
        }

        return ResponseEntity.ok("멤버를 추가했습니다.");
    }

    /** 퇴장(본인) — 공대장은 퇴장 불가 */
    @PostMapping("/leave")
    public ResponseEntity<?> leave(@PathVariable UUID partyId,
                                   @AuthenticationPrincipal User me) {
        if (me == null) return ResponseEntity.status(401).body("인증 필요");
        service.leave(partyId, me.getUserId());
        return ResponseEntity.ok("공대에서 퇴장했습니다.");
    }

    /** 추방(공대장만) */
    @PostMapping("/{userId}/kick")
    public ResponseEntity<?> kick(@PathVariable UUID partyId,
                                  @PathVariable Long userId,
                                  @AuthenticationPrincipal User me) {
        if (me == null) return ResponseEntity.status(401).body("인증 필요");
        service.kick(partyId, me.getUserId(), userId);
        return ResponseEntity.ok("해당 멤버를 추방했습니다.");
    }
}