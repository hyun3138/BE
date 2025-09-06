package com.example.Loark.Repository;

import com.example.Loark.Entity.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface PartyRepository extends JpaRepository<Party, java.util.UUID> {
    boolean existsByPartyIdAndOwner_UserId(java.util.UUID partyId, Long ownerId);
}
