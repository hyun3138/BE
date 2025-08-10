package com.example.Loark.Repository;

import com.example.Loark.Entity.Friend;
import com.example.Loark.Entity.FriendStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface FriendRepository extends JpaRepository<Friend, Long> {

    // 두 유저 간의 관계
    @Query("""
            SELECT f
            FROM Friend f
            WHERE (f.requester.userId = :a AND f.target.userId = :b)
               OR (f.requester.userId = :b AND f.target.userId = :a)
            """)
    Optional<Friend> findAnyBetween(@Param("a") Long userA, @Param("b") Long userB);

    // ACCEPTED 상태 여부 확인
    @Query("""
            SELECT (COUNT(f) > 0)
            FROM Friend f
            WHERE f.status = com.example.Loark.Entity.FriendStatus.ACCEPTED
              AND (
                    (f.requester.userId = :a AND f.target.userId = :b)
                 OR (f.requester.userId = :b AND f.target.userId = :a)
                  )
            """)
    boolean existsAcceptedBetween(@Param("a") Long userA, @Param("b") Long userB);

    // 내가 보낸 대기 목록
    @EntityGraph(attributePaths = {"requester", "target"})
    @Query("""
            SELECT f
            FROM Friend f
            WHERE f.requester.userId = :me
              AND f.status = com.example.Loark.Entity.FriendStatus.PENDING
            """)
    Page<Friend> findPendingOut(@Param("me") Long me, Pageable pageable);

    // 내가 받은 대기 목록
    @EntityGraph(attributePaths = {"requester", "target"})
    @Query("""
            SELECT f
            FROM Friend f
            WHERE f.target.userId = :me
              AND f.status = com.example.Loark.Entity.FriendStatus.PENDING
            """)
    Page<Friend> findPendingIn(@Param("me") Long me, Pageable pageable);

    // ACCEPTED 친구 목록
    @EntityGraph(attributePaths = {"requester", "target"})
    @Query("""
            SELECT f
            FROM Friend f
            WHERE f.status = com.example.Loark.Entity.FriendStatus.ACCEPTED
              AND (f.requester.userId = :me OR f.target.userId = :me)
            """)
    Page<Friend> findAccepted(@Param("me") Long me, Pageable pageable);

    // 닉네임(표시명) 검색  🔁 displayName으로 교체 + 괄호 보정
    @EntityGraph(attributePaths = {"requester", "target"})
    @Query("""
            SELECT f
            FROM Friend f
            WHERE f.status = com.example.Loark.Entity.FriendStatus.ACCEPTED
              AND (
                    (f.requester.userId = :me AND LOWER(f.target.displayName) LIKE LOWER(CONCAT('%', :q, '%')))
                 OR (f.target.userId = :me AND LOWER(f.requester.displayName) LIKE LOWER(CONCAT('%', :q, '%')))
                  )
            """)
    Page<Friend> searchAcceptedByOtherNickname(
            @Param("me") Long me,
            @Param("q") String query,
            Pageable pageable
    );

    // 권한체크
    @Query("""
            SELECT (COUNT(f) > 0)
            FROM Friend f
            WHERE f.friendId = :friendId
              AND (f.requester.userId = :me OR f.target.userId = :me)
            """)
    boolean belongsToUser(@Param("friendId") Long friendId, @Param("me") Long me);

    // 상태별 단건 조회
    Optional<Friend> findByFriendIdAndStatus(Long friendId, FriendStatus status);
}
