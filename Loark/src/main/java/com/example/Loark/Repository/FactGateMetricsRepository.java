package com.example.Loark.Repository;

import com.example.Loark.DTO.FactGateMetricsDto;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.postgresql.util.PGInterval;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Repository
public class FactGateMetricsRepository {

    @PersistenceContext
    private EntityManager entityManager;

    public Optional<FactGateMetricsDto> findLatestByCharacterNameAndRaidName(String characterName, String raidName) {
        String sql = """
            SELECT raid_name, gate_number, difficulty, play_time, ts
            FROM statistic.fact_gate_metrics
            WHERE character_id = :characterName AND raid_name = :raidName
            ORDER BY ts DESC
            LIMIT 1
            """;
        try {
            Object[] result = (Object[]) entityManager.createNativeQuery(sql, Object[].class)
                    .setParameter("characterName", characterName)
                    .setParameter("raidName", raidName)
                    .getSingleResult();

            return Optional.of(FactGateMetricsDto.builder()
                    .raidName((String) result[0])
                    .gateNumber(((Number) result[1]).shortValue())
                    .difficulty((String) result[2])
                    .playTime(toDuration(result[3])) // 변환 메소드 사용
                    .ts(result[4] instanceof Instant ? (Instant) result[4] : null)
                    .build());
        } catch (jakarta.persistence.NoResultException e) {
            return Optional.empty();
        }
    }

    public int updatePartyRunId(UUID partyRunId, String raidName, Short gateNumber, String difficulty, double startSeconds, double endSeconds, List<String> characterNicknames) {
        String sql = """
            UPDATE statistic.fact_gate_metrics
            SET party_run_id = :partyRunId
            WHERE raid_name = :raidName
              AND gate_number = :gateNumber
              AND difficulty = CAST(:difficulty AS statistic.raid_difficulty)
              AND play_time BETWEEN make_interval(secs => :startSeconds) AND make_interval(secs => :endSeconds)
              AND character_id IN (:characterNicknames)
              AND party_run_id IS NULL
            """;
        return entityManager.createNativeQuery(sql)
                .setParameter("partyRunId", partyRunId)
                .setParameter("raidName", raidName)
                .setParameter("gateNumber", gateNumber)
                .setParameter("difficulty", difficulty)
                .setParameter("startSeconds", startSeconds)
                .setParameter("endSeconds", endSeconds)
                .setParameter("characterNicknames", characterNicknames)
                .executeUpdate();
    }

    public List<FactGateMetricsDto> findAllByPartyRunId(UUID partyRunId) {
        String sql = """
            SELECT
                raid_name, gate_number, difficulty, play_time,
                character_id, class_name, role, dps, synergy_efficiency_rate,
                back_attack_rate, head_attack_rate, crit_rate, total_damage,
                support_dps, support_attack_buff_effectiveness_rate,
                support_brand_buff_effectiveness_rate, support_damage_buff2_effectiveness_rate,
                support_damage_increase_effectiveness_rate, support_assist_total_damage
            FROM
                statistic.fact_gate_metrics
            WHERE
                party_run_id = :partyRunId
            """;

        List<Object[]> results = entityManager.createNativeQuery(sql, Object[].class)
                .setParameter("partyRunId", partyRunId)
                .getResultList();

        return results.stream()
                .map(row -> FactGateMetricsDto.builder()
                        .raidName((String) row[0])
                        .gateNumber(row[1] != null ? ((Number) row[1]).shortValue() : null)
                        .difficulty((String) row[2])
                        .playTime(toDuration(row[3])) // 변환 메소드 사용
                        .characterId((String) row[4])
                        .className((String) row[5])
                        .role((String) row[6])
                        .dps(row[7] != null ? ((Number) row[7]).doubleValue() : null)
                        .synergyEfficiencyRate(row[8] != null ? ((Number) row[8]).doubleValue() : null)
                        .backAttackRate(row[9] != null ? ((Number) row[9]).doubleValue() : null)
                        .headAttackRate(row[10] != null ? ((Number) row[10]).doubleValue() : null)
                        .critRate(row[11] != null ? ((Number) row[11]).doubleValue() : null)
                        .totalDamage(row[12] != null ? new BigDecimal(row[12].toString()) : null)
                        .supportDps(row[13] != null ? ((Number) row[13]).doubleValue() : null)
                        .supportAttackBuffEffectivenessRate(row[14] != null ? ((Number) row[14]).doubleValue() : null)
                        .supportBrandBuffEffectivenessRate(row[15] != null ? ((Number) row[15]).doubleValue() : null)
                        .supportDamageBuff2EffectivenessRate(row[16] != null ? ((Number) row[16]).doubleValue() : null)
                        .supportDamageIncreaseEffectivenessRate(row[17] != null ? ((Number) row[17]).doubleValue() : null)
                        .supportAssistTotalDamage(row[18] != null ? new BigDecimal(row[18].toString()) : null)
                        .build())
                .collect(Collectors.toList());
    }

    public List<FactGateMetricsDto> findAllByCharacterName(String characterName) {
        String sql = """
            SELECT
                raid_name, gate_number, difficulty, play_time,
                character_id, class_name, role, dps, synergy_efficiency_rate,
                back_attack_rate, head_attack_rate, crit_rate, total_damage,
                support_dps, support_attack_buff_effectiveness_rate,
                support_brand_buff_effectiveness_rate, support_damage_buff2_effectiveness_rate,
                support_damage_increase_effectiveness_rate, support_assist_total_damage
            FROM
                statistic.fact_gate_metrics
            WHERE
                character_id = :characterName
            ORDER BY ts DESC
            """;

        List<Object[]> results = entityManager.createNativeQuery(sql, Object[].class)
                .setParameter("characterName", characterName)
                .getResultList();

        return results.stream()
                .map(row -> FactGateMetricsDto.builder()
                        .raidName((String) row[0])
                        .gateNumber(row[1] != null ? ((Number) row[1]).shortValue() : null)
                        .difficulty((String) row[2])
                        .playTime(toDuration(row[3]))
                        .characterId((String) row[4])
                        .className((String) row[5])
                        .role((String) row[6])
                        .dps(row[7] != null ? ((Number) row[7]).doubleValue() : null)
                        .synergyEfficiencyRate(row[8] != null ? ((Number) row[8]).doubleValue() : null)
                        .backAttackRate(row[9] != null ? ((Number) row[9]).doubleValue() : null)
                        .headAttackRate(row[10] != null ? ((Number) row[10]).doubleValue() : null)
                        .critRate(row[11] != null ? ((Number) row[11]).doubleValue() : null)
                        .totalDamage(row[12] != null ? new BigDecimal(row[12].toString()) : null)
                        .supportDps(row[13] != null ? ((Number) row[13]).doubleValue() : null)
                        .supportAttackBuffEffectivenessRate(row[14] != null ? ((Number) row[14]).doubleValue() : null)
                        .supportBrandBuffEffectivenessRate(row[15] != null ? ((Number) row[15]).doubleValue() : null)
                        .supportDamageBuff2EffectivenessRate(row[16] != null ? ((Number) row[16]).doubleValue() : null)
                        .supportDamageIncreaseEffectivenessRate(row[17] != null ? ((Number) row[17]).doubleValue() : null)
                        .supportAssistTotalDamage(row[18] != null ? new BigDecimal(row[18].toString()) : null)
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * 데이터베이스의 Interval 타입을 Duration 타입으로 변환하는 헬퍼 메소드
     */
    private Duration toDuration(Object obj) {
        if (obj == null) {
            return null;
        }
        if (obj instanceof PGInterval) {
            PGInterval pgInterval = (PGInterval) obj;
            return Duration.ofDays(pgInterval.getDays())
                    .plusHours(pgInterval.getHours())
                    .plusMinutes(pgInterval.getMinutes())
                    .plusSeconds((long) pgInterval.getSeconds());
        }
        if (obj instanceof Duration) {
            return (Duration) obj;
        }
        return null;
    }
}
