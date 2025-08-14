package com.example.Loark.DTO;

import lombok.*;
@Getter @Setter @Builder @AllArgsConstructor @NoArgsConstructor
public class PartyResponse {
    private String partyId;     // UUID 문자열
    private String name;
    private String visibility;  // "private" | "public"
    private Long ownerUserId;
    private String createdAt;   // ISO 문자열 (간단화를 위해 String)
}
