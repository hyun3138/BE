package com.example.Loark.Service;
import com.example.Loark.Entity.*;
import com.example.Loark.Repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.*;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class FriendServiceTest {

    @Mock FriendRepository friendRepository;
    @Mock BlockedUserRepository blockedUserRepository;
    @Mock UserRepository userRepository;

    @InjectMocks FriendService friendService;

    Long aId; // me
    Long bId; // target
    Long cId; // third

    User A; User B; User C;

    @BeforeEach
    void setUp() {
        aId = 1L; bId = 2L; cId = 3L;
        A = User.builder().userId(aId).userNickname("A").build();
        B = User.builder().userId(bId).userNickname("B").build();
        C = User.builder().userId(cId).userNickname("C").build();
    }

    @Test
    void 친구요청_성공_Pending생성() {
        when(blockedUserRepository.existsAnyBlockBetween(aId, bId)).thenReturn(false);
        when(friendRepository.findAnyBetween(aId, bId)).thenReturn(Optional.empty());
        when(userRepository.findById(bId)).thenReturn(Optional.of(B));
        when(userRepository.getReferenceById(aId)).thenReturn(A);

        friendService.sendRequest(aId, bId);

        verify(friendRepository).save(argThat(f ->
                f.getStatus() == FriendStatus.PENDING &&
                        f.getRequester().getUserId().equals(aId) &&
                        f.getTarget().getUserId().equals(bId)
        ));
    }

    @Test
    void 친구요청_역방향대기가있으면_자동수락() {
        Friend pending = Friend.builder()
                .friendId(10L)
                .requester(B)
                .target(A)
                .status(FriendStatus.PENDING)
                .build();
        when(userRepository.findById(bId)).thenReturn(Optional.of(B));
        when(blockedUserRepository.existsAnyBlockBetween(aId, bId)).thenReturn(false);
        when(friendRepository.findAnyBetween(aId, bId)).thenReturn(Optional.of(pending));

        friendService.sendRequest(aId, bId);

        assertThat(pending.getStatus()).isEqualTo(FriendStatus.ACCEPTED);
        assertThat(pending.getRespondedAt()).isNotNull();
        verify(friendRepository).save(pending);
        verify(friendRepository, never()).save(argThat(f -> f.getFriendId() == null));
    }

    @Test
    void 친구요청_차단관계면_불가() {
        when(userRepository.findById(bId)).thenReturn(Optional.of(B));
        when(blockedUserRepository.existsAnyBlockBetween(aId, bId)).thenReturn(true);
        assertThatThrownBy(() -> friendService.sendRequest(aId, bId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("차단 관계");
        verify(friendRepository, never()).save(any());
    }

    @Test
    void 수락_받은사람만가능_그외예외() {
        Friend f = Friend.builder()
                .friendId(11L)
                .requester(A)
                .target(B)
                .status(FriendStatus.PENDING)
                .build();
        when(friendRepository.findByFriendIdAndStatus(11L, FriendStatus.PENDING)).thenReturn(Optional.of(f));

        // 요청자가 수락 시도 → 실패
        assertThatThrownBy(() -> friendService.accept(aId, 11L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("내가 받은 요청만 수락");

        // 대상이 수락 → 성공
        friendService.accept(bId, 11L);
        assertThat(f.getStatus()).isEqualTo(FriendStatus.ACCEPTED);
        assertThat(f.getRespondedAt()).isNotNull();
        verify(friendRepository).save(f);
    }

    @Test
    void 거절_받은사람만가능() {
        Friend f = Friend.builder()
                .friendId(12L)
                .requester(A)
                .target(B)
                .status(FriendStatus.PENDING)
                .build();
        when(friendRepository.findByFriendIdAndStatus(12L, FriendStatus.PENDING)).thenReturn(Optional.of(f));

        assertThatThrownBy(() -> friendService.decline(aId, 12L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("내가 받은 요청만 거절");

        friendService.decline(bId, 12L);
        assertThat(f.getStatus()).isEqualTo(FriendStatus.DECLINED);
        assertThat(f.getRespondedAt()).isNotNull();
        verify(friendRepository).save(f);
    }

    @Test
    void 삭제_권한없으면예외() {
        when(friendRepository.belongsToUser(13L, cId)).thenReturn(false);
        assertThatThrownBy(() -> friendService.delete(cId, 13L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("삭제 권한 없음");

        when(friendRepository.belongsToUser(13L, aId)).thenReturn(true);
        friendService.delete(aId, 13L);
        verify(friendRepository).deleteById(13L);
    }

    @Test
    void 목록조회_PendingIn_Out_Accepted_검색() {
        when(friendRepository.findPendingIn(bId, PageRequest.of(0,20))).thenReturn(Page.empty());
        when(friendRepository.findPendingOut(aId, PageRequest.of(0,20))).thenReturn(Page.empty());
        when(friendRepository.findAccepted(aId, PageRequest.of(0,20))).thenReturn(Page.empty());
        when(friendRepository.searchAcceptedByOtherNickname(eq(aId), eq("B"), any(Pageable.class))).thenReturn(Page.empty());

        Page<Friend> inB = friendService.list(bId, "PENDING_IN", null, PageRequest.of(0,20));
        assertThat(inB.getTotalElements()).isZero();

        Page<Friend> outA = friendService.list(aId, "PENDING_OUT", null, PageRequest.of(0,20));
        assertThat(outA.getTotalElements()).isZero();

        Page<Friend> acceptedA = friendService.list(aId, "ACCEPTED", null, PageRequest.of(0,20));
        assertThat(acceptedA.getTotalElements()).isZero();

        Page<Friend> searchA = friendService.list(aId, "ACCEPTED", "B", PageRequest.of(0,20));
        assertThat(searchA.getTotalElements()).isZero();

        verify(friendRepository).findPendingIn(bId, PageRequest.of(0,20));
        verify(friendRepository).findPendingOut(aId, PageRequest.of(0,20));
        verify(friendRepository).findAccepted(aId, PageRequest.of(0,20));
        verify(friendRepository).searchAcceptedByOtherNickname(eq(aId), eq("B"), any(Pageable.class));
    }

    @Test
    void 차단시_기존친구삭제_그리고차단해제_정상동작() {
        Friend accepted = Friend.builder()
                .friendId(20L)
                .requester(A)
                .target(B)
                .status(FriendStatus.ACCEPTED)
                .build();
        when(blockedUserRepository.existsAnyBlockBetween(aId, bId)).thenReturn(false);
        when(friendRepository.findAnyBetween(aId, bId)).thenReturn(Optional.of(accepted));
        when(userRepository.getReferenceById(aId)).thenReturn(A);
        when(userRepository.getReferenceById(bId)).thenReturn(B);

        friendService.block(aId, bId);
        verify(friendRepository).delete(accepted);
        verify(blockedUserRepository).save(argThat(b ->
                b.getBlocker().getUserId().equals(aId) && b.getBlocked().getUserId().equals(bId)
        ));

        friendService.unblock(aId, bId);
        verify(blockedUserRepository).deleteByBlocker_UserIdAndBlocked_UserId(aId, bId);
    }

    @Test
    void 자기자신에게요청_or_차단시_예외() {
        assertThatThrownBy(() -> friendService.sendRequest(aId, aId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("자기 자신에게 요청 불가");

        assertThatThrownBy(() -> friendService.block(aId, aId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("자기 자신 차단 불가");
    }
}
