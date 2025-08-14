package com.example.Loark.Service;

import com.example.Loark.Entity.*;
import com.example.Loark.Repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PartyInviteService {

    private static final int MAX_MEMBERS = 8;

    private final PartyRepository parties;
    private final PartyInviteRepository invites;
    private final PartyMemberRepository members;
    private final UserRepository users;
    private final FriendRepository friends;
    private final BlockedUserRepository blocks;
    private final PartyAuthz authz;

    /** 초대 생성 (공대장만) */
    @Transactional
    public PartyInvite create(UUID partyId, Long inviterId, Long inviteeId) {
        // 0) 자기 자신 초대 금지
        if (inviterId.equals(inviteeId)) throw new IllegalArgumentException("자기 자신을 초대할 수 없습니다.");

        // 1) 공대 존재 + 공대장 권한
        Party party = parties.findById(partyId)
                .orElseThrow(() -> new IllegalArgumentException("공대를 찾을 수 없습니다."));
        if (!authz.isOwner(partyId, inviterId))
            throw new IllegalStateException("공대장만 초대를 보낼 수 있습니다.");

        // 2) 차단 관계 금지
        if (blocks.existsAnyBlockBetween(inviterId, inviteeId))
            throw new IllegalStateException("차단 관계에서는 초대할 수 없습니다.");

        // 3) 친구(ACCEPTED) 관계만 허용
        if (!friends.existsAcceptedBetween(inviterId, inviteeId))
            throw new IllegalStateException("공대장과 초대 대상은 친구(ACCEPTED)여야 합니다.");

        // 4) 정원 8명 제한 (현재 재직중 멤버 수)
        long cur = members.countByParty_PartyIdAndLeftAtIsNull(partyId);
        if (cur >= MAX_MEMBERS) throw new IllegalStateException("정원이 가득 찼습니다.(최대 8명)");

        // 5) 이미 '현재' 멤버인지 금지 (leftAt IS NULL 만)
        if (members.existsByParty_PartyIdAndUser_UserIdAndLeftAtIsNull(partyId, inviteeId)) {
            throw new IllegalStateException("이미 공대 멤버입니다.");
        }

        // 6) 대기중 초대 중복 금지
        if (invites.existsByParty_PartyIdAndInvitee_UserIdAndStatus(partyId, inviteeId, PartyInviteStatus.PENDING))
            throw new IllegalStateException("이미 대기중인 초대가 있습니다.");

        // 7) 생성
        PartyInvite inv = PartyInvite.builder()
                .party(party)
                .inviter(users.getReferenceById(inviterId))
                .invitee(users.getReferenceById(inviteeId))
                .status(PartyInviteStatus.PENDING)
                .build();
        return invites.save(inv);
    }
    // ✅ 닉네임으로 초대
    @Transactional
    public PartyInvite createByNickname(UUID partyId, Long inviterId, String mainNickname) {
        if (mainNickname == null || mainNickname.isBlank()) {
            throw new IllegalArgumentException("대표 캐릭터 닉네임을 입력하세요.");
        }
        var invitee = users.findByMainCharacterIgnoreCase(mainNickname.trim())
                .orElseThrow(() -> new IllegalArgumentException("해당 대표 캐릭터 닉네임의 사용자를 찾을 수 없습니다."));
        return create(partyId, inviterId, invitee.getUserId()); // 🔁 기존 로직 재사용
    }

    @Transactional
    public void accept(UUID inviteId, Long me) {
        var inv = invites.findById(inviteId)
                .orElseThrow(() -> new IllegalArgumentException("초대를 찾을 수 없습니다."));
        var partyId = inv.getParty().getPartyId();

        // 1) 이미 '현재 멤버'면 차단
        if (members.existsByParty_PartyIdAndUser_UserIdAndLeftAtIsNull(partyId, me)) {
            throw new IllegalStateException("이미 공대 멤버입니다.");
        }

        // 2) 정원 체크 (leftAt IS NULL 기준)
        long cur = members.countByParty_PartyIdAndLeftAtIsNull(partyId);
        if (cur >= MAX_MEMBERS) throw new IllegalStateException("정원이 가득 찼습니다.");

        // 3) 과거 이력 조회 → 복원 또는 신규
        var histOpt = members.findByParty_PartyIdAndUser_UserId(partyId, me);
        if (histOpt.isPresent()) {
            var hist = histOpt.get();
            if (hist.getLeftAt() != null) {
                // ✅ 재입장 복원: joinedAt 보장 + leftAt null
                if (hist.getJoinedAt() == null) {
                    hist.setJoinedAt(OffsetDateTime.now());
                }
                hist.setLeftAt(null);
                hist.setSubparty(null);
                hist.setRole(null);
                hist.setColeader(false);
                members.save(hist);
            } else {
                throw new IllegalStateException("이미 공대 멤버입니다.");
            }
        } else {
            // ✅ 최초 입장: joinedAt 명시
            var m = PartyMember.builder()
                    .id(new PartyMemberId(partyId, me))
                    .party(inv.getParty())
                    .user(inv.getInvitee())
                    .joinedAt(OffsetDateTime.now())
                    .build();
            members.save(m);
        }

        // 4) 초대 상태 갱신
        inv.setStatus(PartyInviteStatus.ACCEPTED);
        inv.setRespondedAt(OffsetDateTime.now());
        invites.save(inv);
    }

    /** 초대 거절 (초대 받은 사람만) */
    @Transactional
    public void decline(UUID inviteId, Long me) {
        PartyInvite inv = invites.findById(inviteId)
                .orElseThrow(() -> new IllegalArgumentException("초대를 찾을 수 없습니다."));
        if (inv.getStatus() != PartyInviteStatus.PENDING)
            throw new IllegalStateException("이미 처리된 초대입니다.");
        if (!inv.getInvitee().getUserId().equals(me))
            throw new IllegalStateException("내가 받은 초대만 거절할 수 있습니다.");

        inv.setStatus(PartyInviteStatus.DECLINED);
        inv.setRespondedAt(OffsetDateTime.now());
        invites.save(inv);
    }

    /** 초대 취소 (공대장만) */
    @Transactional
    public void cancel(UUID inviteId, Long me) {
        PartyInvite inv = invites.findById(inviteId)
                .orElseThrow(() -> new IllegalArgumentException("초대를 찾을 수 없습니다."));
        if (inv.getStatus() != PartyInviteStatus.PENDING)
            throw new IllegalStateException("이미 처리된 초대입니다.");

        UUID partyId = inv.getParty().getPartyId();
        if (!authz.isOwner(partyId, me))
            throw new IllegalStateException("공대장만 초대를 취소할 수 있습니다.");

        inv.setStatus(PartyInviteStatus.CANCELED);
        inv.setRespondedAt(OffsetDateTime.now());
        invites.save(inv);
    }

}