package com.example.Loark.DTO;

import com.example.Loark.Entity.FriendStatus;
import lombok.*;
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
}