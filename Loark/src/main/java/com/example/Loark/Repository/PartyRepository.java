package com.example.Loark.Repository;

import com.example.Loark.Entity.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface PartyRepository extends JpaRepository<Party, java.util.UUID> {
    boolean existsByPartyIdAndOwner_UserId(java.util.UUID partyId, Long ownerId);
    // 공개 파티 목록 (페이징/정렬 지원)
    Page<Party> findByVisibility(String visibility, Pageable pageable);
    // 공대 이름 검색
    Page<Party> findByVisibilityAndNameContainingIgnoreCase(String visibility, String name, Pageable pageable);

}