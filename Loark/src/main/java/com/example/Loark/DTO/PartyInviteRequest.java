package com.example.Loark.DTO;

import lombok.Getter; import lombok.Setter;
@Getter @Setter
public class PartyInviteRequest {
    private Long inviteeUserId;
    private String inviteeNickname;
}