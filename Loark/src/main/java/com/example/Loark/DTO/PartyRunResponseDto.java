package com.example.Loark.DTO;

import com.example.Loark.Entity.PartyRun;
import com.example.Loark.Entity.PartyRunResult;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PartyRunResponseDto {
    private UUID partyRunId;
    private UUID partyId;
    private String raidName;
    private Short gateNumber;
    private String difficulty;
    private Duration playTime;
    private PartyRunResult result;
    private Long createdByUserId;
    private Instant createdAt;

    public static PartyRunResponseDto fromEntity(PartyRun entity) {
        return PartyRunResponseDto.builder()
                .partyRunId(entity.getPartyRunId())
                .partyId(entity.getParty().getPartyId())
                .raidName(entity.getRaidName())
                .gateNumber(entity.getGateNumber())
                .difficulty(entity.getDifficulty())
                .playTime(entity.getPlayTime())
                .result(entity.getResult())
                .createdByUserId(entity.getCreatedBy().getUserId())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}
