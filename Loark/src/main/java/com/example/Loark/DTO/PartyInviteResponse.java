package com.example.Loark.DTO;

import com.example.Loark.Entity.PartyInviteStatus;
import lombok.*;
@Getter @Setter @Builder @AllArgsConstructor @NoArgsConstructor
public class PartyInviteResponse {
    private String inviteId;     // UUID
    private String partyId;      // UUID
    private Long inviterUserId;
    private Long inviteeUserId;
    private PartyInviteStatus status;
    private String createdAt;    // ISO string
    private String respondedAt;  // ISO string (nullable)
}