package com.example.Loark.Repository;

import com.example.Loark.Entity.PartyRun;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PartyRunRepository extends JpaRepository<PartyRun, UUID> {
    List<PartyRun> findByParty_PartyIdOrderByCreatedAtDesc(UUID partyId);
}
