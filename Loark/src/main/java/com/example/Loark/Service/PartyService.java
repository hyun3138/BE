package com.example.Loark.Service;

import com.example.Loark.Entity.Party;
import com.example.Loark.Entity.PartyMember;
import com.example.Loark.Entity.PartyMemberId;
import com.example.Loark.Entity.User;
import com.example.Loark.Repository.PartyInviteRepository;
import com.example.Loark.Repository.PartyMemberRepository;
import com.example.Loark.Repository.PartyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PartyService {
    private final PartyRepository partyRepository;
    private final PartyMemberRepository partyMemberRepository;
    private final PartyInviteRepository partyInviteRepository;

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

        // ✅ 공대장 자동 가입 (leftAt=null)
        PartyMember ownerMember = PartyMember.builder()
                .id(new PartyMemberId(p.getPartyId(), me.getUserId()))
                .party(p)
                .user(me)
                .subparty((short) 1) // 파티장은 1번 파티에 자동 배정
                .coleader(true) // 파티장은 부공대장으로 자동 설정
                .build();
        partyMemberRepository.save(ownerMember);

        return p;
    }

    /** 공대 가입: subparty 자동 배정 */
    @Transactional
    public void join(UUID partyId, User user) {
        if (user == null) throw new IllegalStateException("로그인이 필요합니다.");

        Party party = partyRepository.findById(partyId)
                .orElseThrow(() -> new IllegalStateException("존재하지 않는 파티입니다."));

        // 이미 현재 멤버인지 확인
        if (partyMemberRepository.existsByParty_PartyIdAndUser_UserIdAndLeftAtIsNull(partyId, user.getUserId())) {
            throw new IllegalStateException("이미 가입한 파티입니다.");
        }

        // 파티 인원 제한 확인 (최대 8명)
        if (partyMemberRepository.countByParty_PartyIdAndLeftAtIsNull(partyId) >= 8) {
            throw new IllegalStateException("파티가 가득 찼습니다.");
        }

        // 서브파티 배정 로직
        int subpartyToAssign;
        long subparty1Count = partyMemberRepository.countByParty_PartyIdAndSubpartyAndLeftAtIsNull(partyId, 1);

        if (subparty1Count < 4) {
            subpartyToAssign = 1;
        } else {
            subpartyToAssign = 2;
        }

        // 재가입 처리 로직 포함하여 멤버 정보 업데이트
        PartyMember member = partyMemberRepository.findByParty_PartyIdAndUser_UserId(partyId, user.getUserId())
                .orElseGet(() -> PartyMember.builder()
                        .id(new PartyMemberId(partyId, user.getUserId()))
                        .party(party)
                        .user(user)
                        .build());

        member.setSubparty((short) subpartyToAssign);
        member.setRole(null);
        member.setColeader(false);
        member.setLeftAt(null); // 재가입이므로 탈퇴시간 초기화

        partyMemberRepository.save(member);
    }

    @Transactional
    public void changeSubparty(UUID partyId, Long ownerId, Long member1UserId, Long member2UserId) {
        Party party = getOwnedOrThrow(partyId, ownerId); // 1. 소유권 확인

        // 2. 교환할 두 멤버의 정보 조회
        PartyMember member1 = partyMemberRepository.findByParty_PartyIdAndUser_UserId(partyId, member1UserId)
                .filter(m -> m.getLeftAt() == null)
                .orElseThrow(() -> new IllegalStateException("첫 번째 멤버를 찾을 수 없습니다."));

        PartyMember member2 = partyMemberRepository.findByParty_PartyIdAndUser_UserId(partyId, member2UserId)
                .filter(m -> m.getLeftAt() == null)
                .orElseThrow(() -> new IllegalStateException("두 번째 멤버를 찾을 수 없습니다."));

        // 3. 두 멤버가 같은 서브파티면 변경할 필요 없음
        if (member1.getSubparty() == member2.getSubparty()) {
            return; // 혹은 예외 처리
        }

        // 4. 공대장은 1번 서브파티를 떠날 수 없음
        Long partyOwnerId = party.getOwner().getUserId();
        if (partyOwnerId.equals(member1.getUser().getUserId()) || partyOwnerId.equals(member2.getUser().getUserId())) {
            throw new IllegalStateException("공대장은 팀을 변경할 수 없습니다.");
        }

        // 5. 서브파티 맞교환
        short subparty1 = member1.getSubparty();
        member1.setSubparty(member2.getSubparty());
        member2.setSubparty(subparty1);

        partyMemberRepository.save(member1);
        partyMemberRepository.save(member2);
    }

    /** 존재여부 + 소유권 검사 후 반환 */
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
        Party p = getOwnedOrThrow(partyId, user.getUserId()); // 소유권 검증
        partyInviteRepository.deleteAllByParty(p);
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

        // 부공대장 역할 변경
        oldOwnerMember.setColeader(false);;
        newOwnerMember.setColeader(true);

        // ✅ 서브파티 자동 조정 로직
        if (newOwnerMember.getSubparty() == 2) {
            newOwnerMember.setSubparty((short) 1); // 새 공대장은 1팀으로
            oldOwnerMember.setSubparty((short) 2); // 이전 공대장은 2팀으로
        }

        partyMemberRepository.save(oldOwnerMember);
        partyMemberRepository.save(newOwnerMember);

        p.setOwner(newOwnerMember.getUser());
        partyRepository.save(p);
    }

    /** 전체 공개 공대 목록 */
    public Page<Party> listPublic(Pageable pageable) {
        return partyRepository.findAll(pageable);
    }

    /** (옵션) 검색어 포함 공개 공대 목록 */
    public Page<Party> listPublic(String q, Pageable pageable) {
        if (q == null || q.isBlank()) return listPublic(pageable);
        return partyRepository.findByVisibilityAndNameContainingIgnoreCase("공개", q.trim(), pageable);

    }
}
