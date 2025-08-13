package com.example.Loark.Repository;

import com.example.Loark.Entity.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.util.*;

public interface PartyMemberRepository extends JpaRepository<PartyMember, PartyMemberId> {
    long countByParty_PartyIdAndLeftAtIsNull(java.util.UUID partyId);
    boolean existsByParty_PartyIdAndUser_UserId(java.util.UUID partyId, Long userId);
    List<PartyMember> findByParty_PartyId(java.util.UUID partyId);
}