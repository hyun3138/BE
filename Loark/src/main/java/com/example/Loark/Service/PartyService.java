package com.example.Loark.Service;

import com.example.Loark.Entity.Party;
import com.example.Loark.Entity.PartyMember;
import com.example.Loark.Entity.PartyMemberId;
import com.example.Loark.Entity.User;
import com.example.Loark.Repository.PartyMemberRepository;
import com.example.Loark.Repository.UserRepository;
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
    private final PartyRepository parties;
    private final PartyAuthz authz;
    private final PartyMemberRepository members;
    private final UserRepository users;

    /** 공대 생성: owner = me, visibility 검증 */
    @Transactional
    public Party create(User me, String name, String visibility) {
        if (me == null) throw new IllegalStateException("로그인이 필요합니다.");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("공대 이름은 필수입니다.");
        authz.validateVisibility(visibility);

        Party p = parties.save(
                Party.builder().name(name.strip()).owner(me).visibility(visibility).build()
        );

        // ✅ 공대장 자동 가입 (leftAt=null)
        PartyMember ownerMember = PartyMember.builder()
                .id(new PartyMemberId(p.getPartyId(), me.getUserId()))
                .party(p)
                .user(me)
                .subparty(null)
                .role(null)
                .coleader(false) // 요구사항상 부공대장 없음
                .build();
        members.save(ownerMember);

        return p;
    }

    /** 존재여부 + 소유권 검사 후 반환 */
    public Party getOwnedOrThrow(UUID partyId, Long meId) {
        return parties.findById(partyId)
                .filter(p -> p.getOwner().getUserId().equals(meId))
                .orElseThrow(() -> new IllegalStateException("존재하지 않거나 권한이 없습니다."));
    }

    public List<Party> listJoined(Long me) {
        return members.findByUser_UserIdAndLeftAtIsNull(me)
                .stream()
                .map(PartyMember::getParty)
                .toList();
    }

    @Transactional
    public void deleteOwned(UUID partyId, Long meId) {
        Party p = getOwnedOrThrow(partyId, meId); // 소유권 검증

        // 멤버 이력 정리: 재직중(leftAt IS NULL)인 멤버 모두 퇴장 처리
        var all = members.findByParty_PartyId(partyId);
        var now = java.time.OffsetDateTime.now();
        for (var m : all) {
            if (m.getLeftAt() == null) {
                m.setLeftAt(now);
                members.save(m);
            }
        }
        // 마지막으로 공대 삭제
        parties.delete(p);
    }
    @Transactional
    public void transferOwner(UUID partyId, Long ownerId, Long newOwnerUserId) {
        Party p = getOwnedOrThrow(partyId, ownerId); // 현재 내가 오너인지 확인

        // newOwnerUserId가 현재 멤버(재직 중)인지 확인
        boolean isActiveMember = members.existsByParty_PartyIdAndUser_UserIdAndLeftAtIsNull(partyId, newOwnerUserId);
        if (!isActiveMember) {
            throw new IllegalStateException("위임 대상은 현재 공대 멤버여야 합니다.");
        }

        // 위임
        var newOwner = users.findById(newOwnerUserId)
                .orElseThrow(() -> new IllegalArgumentException("대상 유저 없음"));
        p.setOwner(newOwner);
        parties.save(p);

    }
    /** 전체 공개 공대 목록 */
    public Page<Party> listPublic(Pageable pageable) {
        return parties.findByVisibility("public", pageable);
    }

    /** (옵션) 검색어 포함 공개 공대 목록 */
    public Page<Party> listPublic(String q, Pageable pageable) {
        if (q == null || q.isBlank()) return listPublic(pageable);
        return parties.findByVisibilityAndNameContainingIgnoreCase("public", q.trim(), pageable);
    }
}
