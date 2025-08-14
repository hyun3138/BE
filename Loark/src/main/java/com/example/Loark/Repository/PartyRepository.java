package com.example.Loark.Repository;

import com.example.Loark.Entity.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.util.*;

public interface PartyRepository extends JpaRepository<Party, java.util.UUID> {
    List<Party> findByOwner_UserId(Long ownerId);
    boolean existsByPartyIdAndOwner_UserId(java.util.UUID partyId, Long ownerId);
}