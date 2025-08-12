package com.example.Loark.Entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "friend_memos",
        uniqueConstraints = @UniqueConstraint(
                name = "ux_friend_memo_per_user",
                columnNames = {"friend_id", "owner_id"}
        )
)
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
public class Friend_Memo {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long memoId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "friend_id", nullable = false)
    private Friend friend;   // 어떤 친구 관계에 대한 메모인지

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;      // 메모 작성자(친구 관계의 한쪽)

    @Column(name = "memo_text", length = 200)
    private String memoText;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist @PreUpdate
    public void touch() {
        updatedAt = LocalDateTime.now();
    }
}
