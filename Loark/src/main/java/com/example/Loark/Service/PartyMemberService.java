package com.example.Loark.Service;

import com.example.Loark.Entity.*;
import com.example.Loark.Repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
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
    private final FriendRepository friends;
    private final BlockedUserRepository blocks;
    private final CharacterRepository characterRepository;

    @Transactional
    public void addMemberById(UUID partyId, Long ownerId, Long targetUserId) {
        // 1) 자기 자신 추가 금지
        if (ownerId.equals(targetUserId)) {
            throw new IllegalArgumentException("자기 자신을 추가할 수 없습니다.");
        }

        // 2) 공대장 권한 확인
        if (!authz.isOwner(partyId, ownerId)) {
            throw new IllegalStateException("공대장만 멤버를 추가할 수 있습니다.");
        }

        // 3) 차단 관계 금지
        if (blocks.existsAnyBlockBetween(ownerId, targetUserId)) {
            throw new IllegalStateException("차단 관계에서는 추가할 수 없습니다.");
        }

        // 4) 친구(ACCEPTED) 관계만 허용
        if (!friends.existsAcceptedBetween(ownerId, targetUserId)) {
            throw new IllegalStateException("친구 관계인 사용자만 추가할 수 있습니다.");
        }

        // 5) 정원 8명 제한
        if (members.countByParty_PartyIdAndLeftAtIsNull(partyId) >= MAX_MEMBERS) {
            throw new IllegalStateException("정원이 가득 찼습니다.(최대 8명)");
        }

        // 6) 이미 현재 멤버인지 확인
        if (members.existsByParty_PartyIdAndUser_UserIdAndLeftAtIsNull(partyId, targetUserId)) {
            throw new IllegalStateException("이미 공대 멤버입니다.");
        }

        // 7) 서브파티 배정 로직
        long subparty1Count = members.countByParty_PartyIdAndSubpartyAndLeftAtIsNull(partyId, 1);
        short subpartyToAssign = (subparty1Count < 4) ? (short) 1 : (short) 2;

        // 8) 멤버 추가 또는 재가입 처리
        Party party = parties.findById(partyId)
                .orElseThrow(() -> new IllegalArgumentException("공대를 찾을 수 없습니다."));
        User targetUser = users.findById(targetUserId)
                .orElseThrow(() -> new IllegalArgumentException("추가할 사용자를 찾을 수 없습니다."));

        // 추가할 멤버의 메인 캐릭터가 등록되어 있는지 확인
        Character mainCharacter = characterRepository.findByName(targetUser.getMainCharacter())
                .orElseThrow(() -> new IllegalStateException("추가하려는 사용자의 대표 캐릭터가 등록되어 있지 않습니다."));

        PartyMember member = members.findByParty_PartyIdAndUser_UserId(partyId, targetUserId)
                .orElseGet(() -> PartyMember.builder() // 신규 멤버
                        .id(new PartyMemberId(partyId, targetUserId))
                        .party(party)
                        .user(targetUser)
                        .joinedAt(OffsetDateTime.now())
                        .build());

        // 신규 또는 재가입 시 공통으로 설정할 값들
        member.setLeftAt(null);
        member.setSubparty(subpartyToAssign);
        member.setRole(null);
        member.setColeader(false);
        member.setCharacterId(mainCharacter.getCharacterId()); // 조회된 캐릭터 ID로 설정

        members.save(member);
    }

    @Transactional
    public void addMemberByNickname(UUID partyId, Long ownerId, String nickname) {
        if (nickname == null || nickname.isBlank()) {
            throw new IllegalArgumentException("대표 캐릭터 닉네임을 입력하세요.");
        }

        Optional<User> targetUserOpt = users.findByMainCharacterIgnoreCase(nickname.trim());

        if (targetUserOpt.isEmpty()) {
            throw new IllegalArgumentException("해당 대표 캐릭터 닉네임의 사용자를 찾을 수 없습니다.");
        }

        User targetUser = targetUserOpt.get();
        addMemberById(partyId, ownerId, targetUser.getUserId());
    }

    /** 멤버 목록 */
    public List<PartyMember> list(UUID partyId) {
        return members.findByParty_PartyIdAndLeftAtIsNull(partyId);
    }

    /** 퇴장(본인) — 공대장은 퇴장 불가 */
    @Transactional
    public void leave(UUID partyId, Long me) {
        Party party = parties.findById(partyId)
                .orElseThrow(() -> new IllegalArgumentException("공대를 찾을 수 없습니다."));

        if (party.getOwner().getUserId().equals(me)) {
            throw new IllegalStateException("공대장은 퇴장할 수 없습니다. 공대를 삭제하거나 위임 기능을 구현하세요.");
        }

        PartyMember m = members.findByParty_PartyIdAndLeftAtIsNull(partyId).stream()
                .filter(x -> x.getUser().getUserId().equals(me))
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

        PartyMember m = members.findByParty_PartyIdAndLeftAtIsNull(partyId).stream()
                .filter(x -> x.getUser().getUserId().equals(targetUserId))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("해당 유저는 재직중 멤버가 아닙니다."));

        m.setLeftAt(OffsetDateTime.now());
        members.save(m);
    }
}
