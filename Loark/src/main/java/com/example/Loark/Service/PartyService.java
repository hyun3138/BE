package com.example.Loark.Service;

import com.example.Loark.Entity.Character;
import com.example.Loark.Entity.Party;
import com.example.Loark.Entity.PartyMember;
import com.example.Loark.Entity.PartyMemberId;
import com.example.Loark.Entity.PartyRun;
import com.example.Loark.Entity.User;
import com.example.Loark.Repository.CharacterRepository;
import com.example.Loark.Repository.PartyMemberRepository;
import com.example.Loark.Repository.PartyRepository;
import com.example.Loark.Repository.PartyRunMemberRepository;
import com.example.Loark.Repository.PartyRunRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PartyService {
    private final PartyRepository partyRepository;
    private final PartyMemberRepository partyMemberRepository;
    private final PartyRunRepository partyRunRepository;
    private final PartyRunMemberRepository partyRunMemberRepository;
    private final CharacterRepository characterRepository;
    private final RoleService roleService; // RoleService 주입

    /** 공대 생성: owner = me, visibility 검증 */
    @Transactional
    public Party create(User me, String name, String description, String visibility) {
        if (me == null) throw new IllegalStateException("로그인이 필요합니다.");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("공대 이름은 필수입니다.");
        if (visibility == null || visibility.isBlank()) throw new IllegalArgumentException("공개 여부는 필수입니다.");

        Party p = partyRepository.save(
                Party.builder()
                        .name(name.strip())
                        .visibility(visibility)
                        .owner(me)
                        .build()
        );

        // 공대장의 메인 캐릭터가 등록되어 있는지 확인
        Character mainCharacter = characterRepository.findByName(me.getMainCharacter())
                .orElseThrow(() -> new IllegalStateException("파티를 생성하려면 대표 캐릭터가 등록되어 있어야 합니다."));

        // 캐릭터 클래스를 기반으로 role 조회
        String role = roleService.getRoleByKoreanClassName(mainCharacter.getClazz());

        // ✅ 공대장 자동 가입 (leftAt=null)
        PartyMember ownerMember = PartyMember.builder()
                .id(new PartyMemberId(p.getPartyId(), me.getUserId()))
                .party(p)
                .user(me)
                .characterId(mainCharacter.getCharacterId()) // 조회된 캐릭터 ID로 설정
                .position(1) // 파티장은 1번 포지션에 자동 배정
                .coleader(true) // 파티장은 부공대장으로 자동 설정
                .role(role) // 조회된 role 설정
                .build();
        partyMemberRepository.save(ownerMember);

        return p;
    }

    @Transactional
    public void swapPositions(UUID partyId, Long ownerId, Long member1UserId, Long member2UserId) {
        Party party = getOwnedOrThrow(partyId, ownerId);

        PartyMember member1 = partyMemberRepository.findByParty_PartyIdAndUser_UserId(partyId, member1UserId)
                .filter(m -> m.getLeftAt() == null)
                .orElseThrow(() -> new IllegalStateException("첫 번째 멤버를 찾을 수 없습니다."));

        PartyMember member2 = partyMemberRepository.findByParty_PartyIdAndUser_UserId(partyId, member2UserId)
                .filter(m -> m.getLeftAt() == null)
                .orElseThrow(() -> new IllegalStateException("두 번째 멤버를 찾을 수 없습니다."));

        Long partyOwnerId = party.getOwner().getUserId();
        if (partyOwnerId.equals(member1.getUser().getUserId()) || partyOwnerId.equals(member2.getUser().getUserId())) {
            throw new IllegalStateException("공대장은 포지션을 변경할 수 없습니다.");
        }

        Integer position1 = member1.getPosition();
        Integer position2 = member2.getPosition();

        // 한쪽을 null로 만들어 UNIQUE 제약조건을 피합니다.
        member1.setPosition(null);
        partyMemberRepository.saveAndFlush(member1);

        // 순서를 교체합니다.
        member2.setPosition(position1);
        partyMemberRepository.saveAndFlush(member2);

        member1.setPosition(position2);
        partyMemberRepository.save(member1);
    }

    public Party getOwnedOrThrow(UUID partyId, Long meId) {
        return partyRepository.findById(partyId)
                .filter(p -> p.getOwner().getUserId().equals(meId))
                .orElseThrow(() -> new IllegalStateException("존재하지 않거나 권한이 없습니다."));
    }

    public List<Party> listJoined(Long me) {
        return partyMemberRepository.findByUser_UserIdAndLeftAtIsNull(me)
                .stream()
                .map(PartyMember::getParty)
                .toList();
    }

    @Transactional
    public void deleteOwned(UUID partyId, User user) {
        Party p = getOwnedOrThrow(partyId, user.getUserId());

        List<PartyRun> runs = partyRunRepository.findByParty_PartyIdOrderByCreatedAtDesc(partyId);
        if (!runs.isEmpty()) {
            partyRunMemberRepository.deleteAllByPartyRunIn(runs);
            partyRunRepository.deleteAllByParty(p);
        }

        partyMemberRepository.deleteAllByParty(p);
        partyRepository.delete(p);
    }

    @Transactional
    public void transferOwner(UUID partyId, Long ownerId, Long newOwnerUserId) {
        Party p = getOwnedOrThrow(partyId, ownerId);

        PartyMember oldOwnerMember = partyMemberRepository.findByParty_PartyIdAndUser_UserId(partyId, ownerId)
                .filter(m -> m.getLeftAt() == null)
                .orElseThrow(() -> new IllegalStateException("이전 소유자 멤버 정보를 찾을 수 없습니다."));

        PartyMember newOwnerMember = partyMemberRepository.findByParty_PartyIdAndUser_UserId(partyId, newOwnerUserId)
                .filter(m -> m.getLeftAt() == null)
                .orElseThrow(() -> new IllegalStateException("위임 대상은 현재 공대 멤버여야 합니다."));

        oldOwnerMember.setColeader(false);
        newOwnerMember.setColeader(true);

        Integer oldOwnerPosition = oldOwnerMember.getPosition();
        Integer newOwnerPosition = newOwnerMember.getPosition();

        // 한쪽을 null로 만들어 UNIQUE 제약조건을 피합니다.
        oldOwnerMember.setPosition(null);
        partyMemberRepository.saveAndFlush(oldOwnerMember);

        // 순서를 교체합니다.
        newOwnerMember.setPosition(oldOwnerPosition);
        partyMemberRepository.saveAndFlush(newOwnerMember);

        oldOwnerMember.setPosition(newOwnerPosition);
        partyMemberRepository.save(oldOwnerMember);

        p.setOwner(newOwnerMember.getUser());
        partyRepository.save(p);
    }

    @Transactional
    public void updateName(UUID partyId, Long userId, String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("공대 이름은 필수입니다.");
        }
        Party party = getOwnedOrThrow(partyId, userId);
        party.setName(name.strip());
        partyRepository.save(party);
    }
}
