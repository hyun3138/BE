package com.example.Loark.Service;

import com.example.Loark.DTO.PartyMemberResponse;
import com.example.Loark.Entity.Party;
import com.example.Loark.Entity.PartyMember;

public class PartyMemberMapper {
    public static PartyMemberResponse toDto(PartyMember m, Party party) {
        var u = m.getUser();
        return PartyMemberResponse.builder()
                .userId(u.getUserId())
                .displayName(u.getDisplayName())
                .pictureUrl(u.getPictureUrl())
                .position(m.getPosition())
                .role(m.getRole())
                .joinedAt(m.getJoinedAt() != null ? m.getJoinedAt().toString() : null)
                .leftAt(m.getLeftAt() != null ? m.getLeftAt().toString() : null)
                .owner(party.getOwner().getUserId().equals(u.getUserId()))
                .build();
    }
}