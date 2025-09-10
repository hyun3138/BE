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
    private Long characterId;

    @Column(name = "position")
    private Integer position;

    // 'dealer' | 'support' 또는 null
    @Column(name = "role")
    private String role;

    @Column(name = "is_coleader", nullable = false)
    private boolean coleader;

    @Column(name = "joined_at", nullable = false)
    private OffsetDateTime joinedAt;

    @Column(name = "left_at")
    private OffsetDateTime leftAt;

    @PrePersist
    public void prePersist() {
        if (this.joinedAt == null) this.joinedAt = OffsetDateTime.now();
    }

    @PreUpdate
    public void preUpdate() {
        if (this.joinedAt == null) this.joinedAt = OffsetDateTime.now();
    }
}
