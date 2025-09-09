package com.example.Loark.Service;

import com.example.Loark.DTO.CommunityPostRequest;
import com.example.Loark.Entity.CommunityPost;
import com.example.Loark.Entity.User;
import com.example.Loark.Repository.CommunityPostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CommunityService {

    private final CommunityPostRepository communityPostRepository;

    /**
     * 새로운 게시글을 생성합니다.
     * @param author 작성자 (로그인한 유저)
     * @param request 글 내용이 담긴 DTO
     * @return 저장된 CommunityPost 엔티티
     */
    @Transactional
    public CommunityPost createPost(User author, CommunityPostRequest request) {
        if (request.getContent() == null || request.getContent().isBlank()) {
            throw new IllegalArgumentException("글 내용이 비어있습니다.");
        }

        CommunityPost newPost = CommunityPost.builder()
                .author(author)
                .content(request.getContent())
                .build();

        return communityPostRepository.save(newPost);
    }

    /**
     * 모든 게시글을 최신순으로 조회합니다.
     * @return CommunityPost 엔티티 리스트
     */
    public List<CommunityPost> getPosts() {
        return communityPostRepository.findAllByOrderByCreatedAtDesc();
    }
}
