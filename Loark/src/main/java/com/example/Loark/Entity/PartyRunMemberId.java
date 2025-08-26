package com.example.Loark.Entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Embeddable
public class PartyRunMemberId implements Serializable {

    @Column(name = "party_run_id")
    private UUID partyRunId;

    @Column(name = "user_id")
    private Long userId;
}
