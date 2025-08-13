package com.example.Loark.DTO;

import lombok.*;
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class PartyMemberResponse {
    private Long userId;
    private String displayName;
    private String pictureUrl;
    private Short subparty; // 1|2 또는 null
    private String role;    // "dealer" | "support" 또는 null
    private String joinedAt;
    private String leftAt;  // null이면 재직중
    private boolean owner;  // 공대장 여부(표시용)
}