package com.example.Loark.Repository;

import com.example.Loark.Entity.BlockUser;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface  BlockedUserRepository extends JpaRepository<BlockUser, Long> {
    
    // 차단 여부
    boolean existsByBlocker_UserIdAndBlocked_UserId(Long blockerId, Long blockedId);

    @Query("""
            SELECT (COUNT(b) > 0)
            FROM BlockUser b
            WHERE (b.blocker.userId = :a AND b.blocked.userId = :b)
            OR (b.blocker.userId = :b AND b.blocked.userId = :a)
            """)
    boolean existsAnyBlockBetween(@Param("a") Long userA, @Param("b") Long userB);

    // 내 차단 목록
    List<BlockUser> findByBlocker_UserId(Long blockerId);

    // 차단 해제
    void deleteByBlocker_UserIdAndBlocked_UserId(Long blockerId, Long blockedId);
}
