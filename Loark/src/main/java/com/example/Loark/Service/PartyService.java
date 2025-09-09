package com.example.Loark.Service;

import com.example.Loark.Entity.Party;
import com.example.Loark.Entity.PartyMember;
import com.example.Loark.Entity.PartyMemberId;
import com.example.Loark.Entity.PartyRun;
import com.example.Loark.Entity.User;
import com.example.Loark.Repository.PartyMemberRepository;
import com.example.Loark.Repository.PartyRepository;
import com.example.Loark.Repository.PartyRunMemberRepository;
import com.example.Loark.Repository.PartyRunRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
        // 1. 파티 소유권 확인
        Party p = getOwnedOrThrow(partyId, user.getUserId());

        // 2. 이 파티와 관련된 모든 PartyRun 기록을 조회
        List<PartyRun> runs = partyRunRepository.findByParty_PartyIdOrderByCreatedAtDesc(partyId);
        if (!runs.isEmpty()) {
            // 3. PartyRun에 속한 모든 PartyRunMember를 먼저 삭제 (손자 데이터 삭제)
            partyRunMemberRepository.deleteAllByPartyRunIn(runs);
            // 4. 모든 PartyRun을 삭제 (자식 데이터 삭제)
            partyRunRepository.deleteAllByParty(p);
        }

        // 5. 이 파티에 속한 모든 PartyMember를 삭제 (다른 자식 데이터 삭제)
        partyMemberRepository.deleteAllByParty(p);

        // 6. 마지막으로 파티 자체를 삭제 (부모 데이터 삭제)
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
}
