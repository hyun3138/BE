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

    // 1|2 or null (스키마 check 있음)
    @Column(name = "subparty")
    private Short subparty;

    // 'dealer' | 'support' 또는 null
    @Column(name = "role")
    private String role;

    @Column(name = "is_coleader", nullable = false)
    private boolean coleader; // 요구사항상 부공대장 없음 → 항상 false로 사용

    @Column(name = "joined_at", columnDefinition = "timestamptz", nullable = false)
    private OffsetDateTime joinedAt;

    @Column(name = "left_at", columnDefinition = "timestamptz")
    private OffsetDateTime leftAt;

    @PrePersist
    void pre() {
        if (joinedAt == null) joinedAt = OffsetDateTime.now();
        // coleader는 요구사항상 사용 안 함
        // leftAt은 null이면 재직중 상태
    }
}
