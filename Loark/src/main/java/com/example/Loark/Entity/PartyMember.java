package com.example.Loark.Entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "party_members")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PartyMember {
    @EmbeddedId
    private PartyMemberId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("partyId")
    @JoinColumn(name = "party_id", nullable = false)
    private Party party;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("userId")
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "character_id")
    private Long characterId; // 누락되었던 필드 추가

    // 1|2 or null (스키마 check 있음)
    @Column(name = "subparty")
    private Short subparty;

    // 'dealer' | 'support' 또는 null
    @Column(name = "role")
    private String role;

    @Column(name = "is_coleader", nullable = false)
    private boolean coleader; // 요구사항상 부공대장 없음 → 항상 false로 사용

    @Column(name = "joined_at", nullable = false /*, updatable = false ← 레거시 보정 끝날 때까지 잠시 빼두는 걸 추천*/)
    private OffsetDateTime joinedAt;

    @Column(name = "left_at")
    private OffsetDateTime leftAt;

    @PrePersist
    public void prePersist() {
        if (this.joinedAt == null) this.joinedAt = OffsetDateTime.now();
    }

    @PreUpdate
    public void preUpdate() {
        // 레거시 row에 joinedAt=null이 남아있는 경우 보정
        if (this.joinedAt == null) this.joinedAt = OffsetDateTime.now();
    }
}
