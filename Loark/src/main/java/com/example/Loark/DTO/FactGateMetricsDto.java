package com.example.Loark.DTO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FactGateMetricsDto {
    private Long id; // id 필드 추가

    // 기존 필드
    private String raidName;
    private Short gateNumber;
    private String difficulty;
    private Duration playTime;
    private Instant ts; // fact 테이블의 타임스탬프

    // 전투 기록 조회를 위한 추가 필드
    private String characterId;
    private String className;
    private String role;
    private Double dps;
    private Double synergyEfficiencyRate;
    private Double backAttackRate;
    private Double headAttackRate;
    private Double critRate;
    private BigDecimal totalDamage;
    private Double supportDps;
    private Double supportAttackBuffEffectivenessRate;
    private Double supportBrandBuffEffectivenessRate;
    private Double supportDamageBuff2EffectivenessRate;
    private Double supportDamageIncreaseEffectivenessRate;
    private BigDecimal supportAssistTotalDamage;

    // 타입 수정된 필드
    private Integer counterSuccess;
    private Integer staggerDamage;
    private BigDecimal partyHealAmount;
}
