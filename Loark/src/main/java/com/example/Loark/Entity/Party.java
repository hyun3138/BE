package com.example.Loark.Entity;
import jakarta.persistence.*;
import lombok.*;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "parties")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Party {
    @Id
    @Column(name = "party_id", columnDefinition = "uuid")
    private UUID partyId;

    @PrePersist
    void pre() {
        if (partyId == null) partyId = java.util.UUID.randomUUID();
        if (createdAt == null) createdAt = OffsetDateTime.now();
        if (updatedAt == null) updatedAt = createdAt;
    }

    @Column(name = "name", nullable = false)
    private String name;

    // owner_user_id FK → users.user_id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_user_id", nullable = false)
    private User owner;

    // 스키마 enum: 'private' | 'public'
    @Column(name = "visibility", nullable = false)
    private String visibility; // "private" 또는 "public"만 사용(서비스 단계에서 검증)

    @Column(name = "created_at", columnDefinition = "timestamptz", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", columnDefinition = "timestamptz", nullable = false)
    private OffsetDateTime updatedAt;

    @PreUpdate
    void touch() { updatedAt = OffsetDateTime.now(); }

    @OneToMany(mappedBy = "party", cascade = CascadeType.REMOVE, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<PartyMember> members = new ArrayList<>();
}
