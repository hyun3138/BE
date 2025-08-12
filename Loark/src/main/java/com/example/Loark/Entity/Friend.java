package com.example.Loark.Entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;

@Entity
@Table(name = "friends")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Friend {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long friendId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "friend_requester_id", nullable = false)
    private User requester; // 요청한 유저

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "friend_target_id", nullable = false)
    private User target; // 요청 받은 유저

    @Enumerated(EnumType.STRING)
    @Column(name = "friend_status", nullable = false)
    private FriendStatus status = FriendStatus.PENDING;

    @Column(name = "friend_created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "friend_responded_at")
    private LocalDateTime respondedAt;

    @PrePersist
    public void prePersist() {
        if(createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
