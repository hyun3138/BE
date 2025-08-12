package com.example.Loark.Repository;

import com.example.Loark.Entity.Friend_Memo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FriendMemoRepository extends JpaRepository<Friend_Memo, Long> {
    Optional<Friend_Memo> findByFriend_FriendIdAndOwner_UserId(Long friendId, Long ownerId);
    void deleteByFriend_FriendIdAndOwner_UserId(Long friendId, Long ownerId);
    void deleteByFriend_FriendId(Long friendId);
}