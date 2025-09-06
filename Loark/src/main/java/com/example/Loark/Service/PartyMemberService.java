package com.example.Loark.Service;

import com.example.Loark.Entity.*;
import com.example.Loark.Repository.*;
import com.example.Loark.Repository.PartyInviteRepository;
import com.example.Loark.Entity.PartyInviteStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PartyMemberService {

    private static final int MAX_MEMBERS = 8;

    private final PartyRepository parties;
    private final PartyMemberRepository members;
    private final PartyAuthz authz;
    private final UserRepository users;
    private final PartyInviteRepository invites;

    /** 멤버 목록 */
    public List<PartyMember> list(UUID partyId) {
        return members.findByParty_PartyId(partyId);
    }

    /** 퇴장(본인) — 공대장은 퇴장 불가 */
    @Transactional
    public void leave(UUID partyId, Long me) {
        Party party = parties.findById(partyId)
                .orElseThrow(() -> new IllegalArgumentException("공대를 찾을 수 없습니다."));

        if (party.getOwner().getUserId().equals(me)) {
            throw new IllegalStateException("공대장은 퇴장할 수 없습니다. 공대를 삭제하거나 위임 기능을 구현하세요.");
        }

        PartyMember m = members.findByParty_PartyId(partyId).stream()
                .filter(x -> x.getUser().getUserId().equals(me) && x.getLeftAt() == null)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("재직중 멤버가 아닙니다."));

        m.setLeftAt(OffsetDateTime.now());
        members.save(m);
    }

    /** 추방(공대장만) — 공대장 자신 추방 불가 */
    @Transactional
    public void kick(UUID partyId, Long ownerId, Long targetUserId) {
        if (!authz.isOwner(partyId, ownerId)) {
            throw new IllegalStateException("공대장만 추방할 수 있습니다.");
        }
        if (ownerId.equals(targetUserId)) {
            throw new IllegalStateException("공대장을 추방할 수 없습니다.");
        }

        PartyMember m = members.findByParty_PartyId(partyId).stream()
                .filter(x -> x.getUser().getUserId().equals(targetUserId) && x.getLeftAt() == null)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("해당 유저는 재직중 멤버가 아닙니다."));

        m.setLeftAt(OffsetDateTime.now());
        members.save(m);
    }
}