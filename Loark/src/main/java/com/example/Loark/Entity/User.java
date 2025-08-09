package com.example.Loark.Entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "users", indexes = {
        @Index(name = "ix_users_email", columnList = "user_email")
})
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Long userId;

    // ✅ 구글 고유 식별자: 절대 null 금지 + 유니크
    @Column(name = "google_sub", nullable = false, unique = true, length = 128)
    private String googleSub;

    @Column(name = "user_email")
    private String userEmail;          // 구글이 이메일 비공개면 null 가능

    @Column(name = "display_name")
    private String displayName;

    @Column(name = "picture_url", length = 2000)
    private String pictureUrl;

    @Column(name = "user_api_key", length = 4000)
    @com.fasterxml.jackson.annotation.JsonIgnore
    private String userApiKey;         // nullable

    @Column(name = "stove_profile_url", length = 2000)
    private String stoveProfileUrl;

    // ✅ 생성 시 자동 입력 + 수정 불가
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    // 보수적으로 한 번 더 안전장치
    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        // ✅ googleSub 비어 있으면 바로 예외 (DB까지 내려가기 전에 차단)
        if (googleSub == null || googleSub.isBlank()) {
            throw new IllegalStateException("google_sub is required");
        }
    }
}
