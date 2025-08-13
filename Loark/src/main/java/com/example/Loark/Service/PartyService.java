package com.example.Loark.Service;

import com.example.Loark.Entity.Party;
import com.example.Loark.Entity.PartyMember;
import com.example.Loark.Entity.PartyMemberId;
import com.example.Loark.Entity.User;
import com.example.Loark.Repository.PartyMemberRepository;
import com.example.Loark.Repository.PartyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PartyService {
    private final PartyRepository parties;
    private final PartyAuthz authz;
    private final PartyMemberRepository members;

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

    /** 내가 공대장인 공대 목록 */
    public List<Party> listMine(Long meId) {
        return parties.findByOwner_UserId(meId);
    }

    /** 존재여부 + 소유권 검사 후 반환 */
    public Party getOwnedOrThrow(UUID partyId, Long meId) {
        return parties.findById(partyId)
                .filter(p -> p.getOwner().getUserId().equals(meId))
                .orElseThrow(() -> new IllegalStateException("존재하지 않거나 권한이 없습니다."));
    }
}
