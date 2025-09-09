package com.example.Loark.DTO;

import com.example.Loark.Entity.CommunityPost;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommunityPostResponse {

    private Long postId;
    private Long authorUserId;
    private String authorNickname;
    private String content;
    private LocalDateTime createdAt;

    public static CommunityPostResponse fromEntity(CommunityPost post) {
        return CommunityPostResponse.builder()
                .postId(post.getPostId())
                .authorUserId(post.getAuthor().getUserId())
                .authorNickname(post.getAuthor().getMainCharacter()) // User 엔티티의 대표 캐릭터 닉네임 사용
                .content(post.getContent())
                .createdAt(post.getCreatedAt())
                .build();
    }
}
