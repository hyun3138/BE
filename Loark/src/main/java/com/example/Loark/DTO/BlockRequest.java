package com.example.Loark.DTO;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Setter;

// 차단/차단해제 요청에 사용
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
public class BlockRequest {
    private Long blockedUserId;
}
