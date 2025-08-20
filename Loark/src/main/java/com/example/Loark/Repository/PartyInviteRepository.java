package com.example.Loark.Repository;
import com.example.Loark.Entity.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.util.*;

public interface PartyInviteRepository extends JpaRepository<PartyInvite, java.util.UUID> {
    Optional<PartyInvite> findByParty_PartyIdAndInvitee_UserIdAndStatus(
            java.util.UUID partyId, Long inviteeUserId, PartyInviteStatus status);

    List<PartyInvite> findByInvitee_UserIdAndStatus(Long inviteeUserId, PartyInviteStatus status);

    boolean existsByParty_PartyIdAndInvitee_UserIdAndStatus(
            java.util.UUID partyId, Long inviteeUserId, PartyInviteStatus status);

    List<PartyInvite> findByInviter_UserIdAndStatus(Long inviterUserId, PartyInviteStatus status);

    void deleteAllByParty(Party party);
}