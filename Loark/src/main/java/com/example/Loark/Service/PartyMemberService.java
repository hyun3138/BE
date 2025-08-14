package com.example.Loark.Service;

import com.example.Loark.Entity.*;
import com.example.Loark.Repository.*;
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

    /** 멤버 목록 */
    public List<PartyMember> list(UUID partyId) {
        return members.findByParty_PartyId(partyId);
    }

    /** 참가(재참가 포함): 초대 수락은 STEP3에서 처리되지만, 공대장 외 멤버가 재참가할 때 사용 */
    @Transactional
    public void join(UUID partyId, Long me) {
        Party party = parties.findById(partyId)
                .orElseThrow(() -> new IllegalArgumentException("공대를 찾을 수 없습니다."));

        // 이미 재직중이면 금지
        if (members.existsByParty_PartyIdAndUser_UserId(partyId, me)) {
            // exists는 leftAt 상관 없으므로, 레코드를 찾아서 상태 확인
            var current = members.findByParty_PartyId(partyId).stream()
                    .filter(m -> m.getUser().getUserId().equals(me))
                    .findFirst().orElse(null);
            if (current != null && current.getLeftAt() == null) {
                throw new IllegalStateException("이미 공대 멤버입니다.");
            }
        }

        long cur = members.countByParty_PartyIdAndLeftAtIsNull(partyId);
        if (cur >= MAX_MEMBERS) throw new IllegalStateException("정원이 가득 찼습니다.(최대 8명)");

        // 과거 이력 있으면 재참가( leftAt → null, joinedAt 갱신 )
        var existed = members.findByParty_PartyId(partyId).stream()
                .filter(m -> m.getUser().getUserId().equals(me))
                .findFirst().orElse(null);

        if (existed != null) {
            existed.setLeftAt(null);
            existed.setJoinedAt(OffsetDateTime.now());
            members.save(existed);
            return;
        }

        // 신규 참가 (일반 멤버)
        PartyMember m = PartyMember.builder()
                .id(new PartyMemberId(partyId, me))
                .party(party)
                .user(party.getOwner().getUserId().equals(me) ? party.getOwner() : null) // null이면 영속 참조로 대체
                .subparty(null)
                .role(null)
                .coleader(false)
                .build();

        // user 영속 참조 주입 (owner가 아닐 때)
        if (m.getUser() == null) {
            m.setUser(new User()); // 참조 프록시 최적화가 필요하면 getReferenceById 사용
            m.getUser().setUserId(me);
        }

        members.save(m);
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