package com.example.Loark.Service;

import com.example.Loark.Entity.*;
import com.example.Loark.Repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FriendService {



    private final FriendRepository friendRepository;
    private final BlockedUserRepository blockedUserRepository;
    private final UserRepository userRepository;

    // 친구 요청
    @Transactional
    public void sendRequest(Long me, Long targetId) {
        if(me.equals(targetId)) {
            throw new IllegalArgumentException("자기 자신에게 요청 불가");
        }
        var target = userRepository.findById(targetId)
                .orElseThrow(() -> new IllegalArgumentException("대상 유저 없음"));
        
        // 차단 관계 검사
        if (blockedUserRepository.existsAnyBlockBetween(me, targetId)) {
            throw new IllegalStateException("차단 관계에서는 요청 불가");
        }

        // 기존 관계 확인
        var existing = friendRepository.findAnyBetween(me, targetId);
        if(existing.isPresent()) {
            Friend f = existing.get();
            if(f.getStatus() == FriendStatus.PENDING && f.getRequester().getUserId().equals(targetId)) {
                // 역방향 요청 -> 자동 수락
                f.setStatus(FriendStatus.ACCEPTED);
                f.setRespondedAt(LocalDateTime.now());
                friendRepository.save(f);
                return;
            }
            throw new IllegalStateException("이미 요청/친구 관계 존재");
        }

        // 새 요청 생성
        Friend friend = Friend.builder()
                .requester(userRepository.getReferenceById(me))
                .target(target)
                .status(FriendStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .build();
        friendRepository.save(friend);
    }

    // 수락
    @Transactional
    public void accept(Long me, Long friendId) {
        Friend friend = friendRepository.findByFriendIdAndStatus(friendId, FriendStatus.PENDING)
                .orElseThrow(() -> new IllegalArgumentException("대기중인 요청 없음"));
        if(!friend.getTarget().getUserId().equals(me)) {
            throw new IllegalStateException("내가 받은 요청만 수락 가능");
        }
        friend.setStatus(FriendStatus.ACCEPTED);
        friend.setRespondedAt(LocalDateTime.now());
        friendRepository.save(friend);
    }
    
    // 거절
    @Transactional
    public void decline(Long me, Long friendId) {
        Friend friend = friendRepository.findByFriendIdAndStatus(friendId, FriendStatus.PENDING)
                .orElseThrow(() -> new IllegalArgumentException("대기중인 요청 없음"));
        if (!friend.getTarget().getUserId().equals(me)) {
            throw new IllegalStateException("내가 받은 요청만 거절 가능");
        }
        friend.setStatus(FriendStatus.DECLINED);
        friend.setRespondedAt(LocalDateTime.now());
        friendRepository.save(friend);
    }

    // 삭제
    @Transactional
    public void delete(Long me, Long friendId) {
        if (!friendRepository.belongsToUser(friendId, me)) {
            throw new IllegalStateException("삭제 권한 없음");
        }
        friendRepository.deleteById(friendId);
    }



    // 목록 조회
    public Page<Friend> list(Long me, String status, String query, Pageable pageable) {
        if ("PENDING".equalsIgnoreCase(status)) {
            return friendRepository.findPendingIn(me, pageable);
        } else if ("REQUESTED".equalsIgnoreCase(status)) {
            return friendRepository.findPendingOut(me, pageable);
        } else if ("ACCEPTED".equalsIgnoreCase(status)) {
            if (query != null && !query.isBlank()) {
                return friendRepository.searchAcceptedByOtherNickname(me, query, pageable);
            }
            return friendRepository.findAccepted(me, pageable);
        }
        throw new IllegalArgumentException("잘못된 status 값: " + status);
    }

    // 차단
    @Transactional
    public void block(Long me, Long targetId) {
        if (me.equals(targetId)) throw new IllegalArgumentException("자기 자신 차단 불가");
        if (blockedUserRepository.existsAnyBlockBetween(me, targetId)) {
            throw new IllegalStateException("이미 차단 관계");
        }
        // 기존 친구 관계 제거
        friendRepository.findAnyBetween(me, targetId).ifPresent(friendRepository::delete);

        BlockUser block = BlockUser.builder()
                .blocker(userRepository.getReferenceById(me))
                .blocked(userRepository.getReferenceById(targetId))
                .createdAt(LocalDateTime.now())
                .build();
        blockedUserRepository.save(block);
    }

    // 차단 해제
    @Transactional
    public void unblock(Long me, Long targetId) {
        blockedUserRepository.deleteByBlocker_UserIdAndBlocked_UserId(me, targetId);
    }
}
