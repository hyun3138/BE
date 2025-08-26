package com.example.Loark.Repository;

import com.example.Loark.Entity.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.util.*;

public interface PartyMemberRepository extends JpaRepository<PartyMember, PartyMemberId> {
    long countByParty_PartyIdAndLeftAtIsNull(java.util.UUID partyId);

    long countByParty_PartyIdAndSubpartyAndLeftAtIsNull(UUID partyId, int subparty);

    List<PartyMember> findByParty_PartyId(java.util.UUID partyId);
    // 현재 멤버 여부 (나가지 않은 사람만 true)
    boolean existsByParty_PartyIdAndUser_UserIdAndLeftAtIsNull(UUID partyId, Long userId);

    // 과거 이력 포함 단건 조회(재입장 처리용)
    Optional<PartyMember> findByParty_PartyIdAndUser_UserId(UUID partyId, Long userId);

    List<PartyMember> findByUser_UserIdAndLeftAtIsNull(Long userId);

    List<PartyMember> findByParty_PartyIdAndLeftAtIsNull(UUID partyId);
}