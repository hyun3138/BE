package com.example.Loark.Service;

import com.example.Loark.DTO.PartyResponse;
import com.example.Loark.Entity.Party;

public class PartyMapper {
    public static PartyResponse toDto(Party p) {
        return PartyResponse.builder()
                .partyId(p.getPartyId().toString())
                .name(p.getName())
                .visibility(p.getVisibility())
                .ownerUserId(p.getOwner().getUserId())
                .createdAt(p.getCreatedAt().toString())
                .build();
    }
}
