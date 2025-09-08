package com.example.Loark.DTO;

import com.example.Loark.Entity.FriendStatus;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;

@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FriendResponse {
    private Long friendId;
    private Long otherUserId;
    private String otherNickname;
    private FriendStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime respondedAt;
    private String friendMemo;
    private OffsetDateTime friendMemoUpdatedAt;
    private String mainCharacterName;
    private String characterClass;
    private String server;
    private BigDecimal itemLevel;
}