package com.example.Loark.Controller;

import com.example.Loark.DTO.CommunityPostRequest;
import com.example.Loark.DTO.CommunityPostResponse;
import com.example.Loark.Entity.CommunityPost;
import com.example.Loark.Entity.User;
import com.example.Loark.Service.CommunityService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/community")
@RequiredArgsConstructor
public class CommunityController {

    private final CommunityService communityService;

    /**
     * 게시글 작성
     */
    @PostMapping
    public ResponseEntity<CommunityPostResponse> createPost(@AuthenticationPrincipal User me,
                                                            @RequestBody CommunityPostRequest request) {
        if (me == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        CommunityPost createdPost = communityService.createPost(me, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(CommunityPostResponse.fromEntity(createdPost));
    }

    /**
     * 게시글 전체 목록 조회
     */
    @GetMapping
    public ResponseEntity<List<CommunityPostResponse>> getAllPosts() {
        List<CommunityPost> posts = communityService.getPosts();
        List<CommunityPostResponse> responseList = posts.stream()
                .map(CommunityPostResponse::fromEntity)
                .collect(Collectors.toList());
        return ResponseEntity.ok(responseList);
    }
}
