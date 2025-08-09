package com.example.Loark.DTO;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Setter;

/**
 * 친구 요청 보낼 때 사용.
 * - targetUserId: 사용자 ID로 요청
 * - targetNickname: 닉네임으로 요청 (선택사항, 나중에 지원)
 * 둘 중 하나만 쓰면 됨. 지금은 targetUserId 위주로 사용할 예정.
 */
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
public class FriendRequest {
    private Long targetUserId;
    private String targetNickname; // 옵션 (미사용이면 null)
}
