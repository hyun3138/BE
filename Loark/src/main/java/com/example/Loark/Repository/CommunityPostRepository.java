package com.example.Loark.Repository;

import com.example.Loark.Entity.CommunityPost;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CommunityPostRepository extends JpaRepository<CommunityPost, Long> {

    /**
     * 모든 게시글을 최신순(내림차순)으로 조회합니다.
     */
    List<CommunityPost> findAllByOrderByCreatedAtDesc();
}
