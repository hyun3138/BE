package com.example.Loark.Service;

import com.example.Loark.DTO.PartyInviteResponse;
import com.example.Loark.Entity.PartyInvite;

public class PartyInviteMapper {
    public static PartyInviteResponse toDto(PartyInvite i) {
        return PartyInviteResponse.builder()
                .inviteId(i.getInviteId().toString())
                .partyId(i.getParty().getPartyId().toString())
                .inviterUserId(i.getInviter().getUserId())
                .inviteeUserId(i.getInvitee().getUserId())
                .status(i.getStatus())
                .createdAt(i.getCreatedAt().toString())
                .respondedAt(i.getRespondedAt() != null ? i.getRespondedAt().toString() : null)
                .build();
    }
}