package com.example.Loark.DTO;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Setter;

@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
public class FriendRequest {
    private Long targetUserId;
    private String targetMainCharacter; // 메인 캐릭터 이름
}
