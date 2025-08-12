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

    private static final int MEMO_MAX_LEN = 50;

    private final FriendRepository friendRepository;
    private final BlockedUserRepository blockedUserRepository;
    private final UserRepository userRepository;
    private final FriendMemoRepository friendMemoRepository;

    // 친구 요청
    @Transactional
    public void sendRequest(Long me, String targetMainCharacter) {
        var target = userRepository.findByMainCharacter(targetMainCharacter)
                .orElseThrow(() -> new IllegalArgumentException("해당 메인 캐릭터를 가진 유저를 찾을 수 없습니다."));

        Long targetId = target.getUserId();

        if (me.equals(targetId)) {
            throw new IllegalArgumentException("자기 자신에게 요청 불가");
        }
        
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
            // ✅ 과거에 거절/취소된 기록은 재요청 가능하도록 덮어쓰기
            if (f.getStatus() == FriendStatus.DECLINED || f.getStatus() == FriendStatus.CANCELED) {
                f.setStatus(FriendStatus.PENDING);
                f.setRequester(userRepository.getReferenceById(me));
                f.setTarget(userRepository.getReferenceById(targetId));
                f.setRespondedAt(null);
                f.setCreatedAt(LocalDateTime.now());
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
        friendMemoRepository.deleteByFriend_FriendId(friendId); // ✅ 메모 선삭제
        friendRepository.deleteById(friendId);
    }

    @Transactional
    public void cancel(Long me, Long friendId) {
        // 1) 대기중(PENDING) 요청만 취소 가능
        Friend f = friendRepository.findByFriendIdAndStatus(friendId, FriendStatus.PENDING)
                .orElseThrow(() -> new IllegalArgumentException("대기 중인 친구 요청이 없습니다."));

        // 2) 내가 '요청자(requester)'인 경우에만 취소 가능
        if (!f.getRequester().getUserId().equals(me)) {
            throw new IllegalStateException("내가 보낸 요청만 취소할 수 있습니다.");
        }

        // 3) 상태를 CANCELED로 전환
        f.setStatus(FriendStatus.CANCELED);
        f.setRespondedAt(LocalDateTime.now());
        friendRepository.save(f);
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

    /** ✅ 개인 메모 저장/수정 */
    @Transactional
    public void updateMemo(Long me, Long friendId, String memo) {
        // 이 친구 관계의 구성원인지 권한 체크
        if (!friendRepository.belongsToUser(friendId, me)) {
            throw new IllegalStateException("권한이 없습니다.");
        }
        Friend friend = friendRepository.findById(friendId)
                .orElseThrow(() -> new IllegalArgumentException("친구 관계가 없습니다."));

        String normalized = (memo == null) ? null : memo.strip();
        if (normalized != null && normalized.length() > MEMO_MAX_LEN) {
            normalized = normalized.substring(0, MEMO_MAX_LEN);
        }

        Friend_Memo myMemo = friendMemoRepository
                .findByFriend_FriendIdAndOwner_UserId(friendId, me)
                .orElseGet(() -> Friend_Memo.builder()
                        .friend(friend)
                        .owner(userRepository.getReferenceById(me))
                        .build());

        myMemo.setMemoText(normalized);
        friendMemoRepository.save(myMemo);
    }

    /** ✅ 개인 메모 삭제 (내 메모만) */
    @Transactional
    public void clearMemo(Long me, Long friendId) {
        if (!friendRepository.belongsToUser(friendId, me)) {
            throw new IllegalStateException("권한이 없습니다.");
        }
        friendMemoRepository.deleteByFriend_FriendIdAndOwner_UserId(friendId, me);
    }

    /** ✅ 내 관점에서 본 메모를 읽을 때 사용할 헬퍼 (컨트롤러에서 DTO 매핑 시 사용) */
    public Friend_Memo findMyMemo(Long me, Long friendId) {
        return friendMemoRepository.findByFriend_FriendIdAndOwner_UserId(friendId, me).orElse(null);
    }

}
