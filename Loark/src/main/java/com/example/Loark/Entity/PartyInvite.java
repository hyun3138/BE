package com.example.Loark.Entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "party_invites")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PartyInvite {
    @Id
    @Column(name = "invite_id", columnDefinition = "uuid")
    private UUID inviteId;

    @PrePersist
    void pre() {
        if (inviteId == null) inviteId = UUID.randomUUID();
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "party_id", nullable = false)
    private Party party;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "inviter_user_id", nullable = false)
    private User inviter;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invitee_user_id", nullable = false)
    private User invitee;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private PartyInviteStatus status;

    @Column(name = "created_at", columnDefinition = "timestamptz", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "responded_at", columnDefinition = "timestamptz")
    private OffsetDateTime respondedAt;
}
