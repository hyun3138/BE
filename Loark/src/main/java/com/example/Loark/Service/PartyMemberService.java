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

    /** 참가(재참가 포함): 초대 수락은 STEP3에서 처리되지만, 공대장 외 멤버가 재참가할 때 사용 */
    @Transactional
    public void join(UUID partyId, Long me) {
        Party party = parties.findById(partyId)
                .orElseThrow(() -> new IllegalArgumentException("공대를 찾을 수 없습니다."));

        // ✅ private 파티는 직접 join 금지 (초대 수락 API를 사용해야 함)
        if ("private".equalsIgnoreCase(party.getVisibility())) {
            throw new IllegalStateException("비공개 공대는 초대를 수락해야만 가입할 수 있습니다."); // 400/409 계열로 처리됨
            // (프론트는 /api/parties/{partyId}/invites/received로 초대 조회 → accept 호출)
        }

// 현재 멤버면 즉시 차단 (LeftAt IS NULL 기준)
        if (members.existsByParty_PartyIdAndUser_UserIdAndLeftAtIsNull(partyId, me)) {
            throw new IllegalStateException("이미 공대 멤버입니다.");
        }

        long cur = members.countByParty_PartyIdAndLeftAtIsNull(partyId);
        if (cur >= MAX_MEMBERS) throw new IllegalStateException("정원이 가득 찼습니다.(최대 8명)");

// 과거 이력 있으면 복원, 없으면 신규
        var histOpt = members.findByParty_PartyIdAndUser_UserId(partyId, me);
        if (histOpt.isPresent()) {
            var hist = histOpt.get();
            if (hist.getLeftAt() != null) {
                hist.setLeftAt(null);
                hist.setSubparty(null);
                hist.setRole(null);
                hist.setColeader(false);
                members.save(hist);
            } else {
                throw new IllegalStateException("이미 공대 멤버입니다.");
            }
        } else {
            // 최초 입장
            User user = users.findById(me).orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다.")); // 2. ID로 User 엔티티 조회
            var m = PartyMember.builder()
                    .id(new PartyMemberId(partyId, me))
                    .party(party)
                    .user(user) // 조회한 User 엔티티 사용
                    .build();
            members.save(m);
        }
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