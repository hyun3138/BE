package com.example.Loark.Entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.GenericGenerator;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "party_runs", indexes = {
        @Index(name = "ix_party_runs_party", columnList = "party_id"),
        @Index(name = "ix_party_runs_scope", columnList = "raid_name, gate_number, difficulty, createdAt")
})
public class PartyRun {

    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(name = "party_run_id", updatable = false, nullable = false)
    private UUID partyRunId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "party_id", nullable = false)
    private Party party;

    @Column(name = "raid_name", nullable = false)
    private String raidName;

    @Column(name = "gate_number", nullable = false)
    private Short gateNumber;

    @Column(nullable = false)
    private String difficulty;

    @Column(name = "play_time")
    private Duration playTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private PartyRunResult result = PartyRunResult.UNKNOWN;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id")
    private User createdBy;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
}
