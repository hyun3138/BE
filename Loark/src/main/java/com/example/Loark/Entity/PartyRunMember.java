package com.example.Loark.Entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "party_run_members", indexes = {
        @Index(name = "ix_party_run_members_user", columnList = "user_id")
})
public class PartyRunMember {

    @EmbeddedId
    private PartyRunMemberId id;

    @MapsId("partyRunId")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "party_run_id", nullable = false)
    private PartyRun partyRun;

    @MapsId("userId")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "character_id")
    private Long characterId;

    private Short subparty;

    private String role;
}
