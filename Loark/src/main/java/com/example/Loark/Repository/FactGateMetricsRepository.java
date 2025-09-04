package com.example.Loark.Repository;

import com.example.Loark.DTO.FactGateMetricsDto;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.postgresql.util.PGInterval;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Array;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Repository
public class FactGateMetricsRepository {

    @PersistenceContext
    private EntityManager entityManager;

    public Optional<String> findCharacterNameById(Long id) {
        String sql = "SELECT character_id FROM statistic.fact_gate_metrics WHERE id = :id";
        try {
            String characterName = (String) entityManager.createNativeQuery(sql, String.class)
                    .setParameter("id", id)
                    .getSingleResult();
            return Optional.ofNullable(characterName);
        } catch (jakarta.persistence.NoResultException e) {
            return Optional.empty();
        }
    }

    public int deleteById(Long id) {
        String sql = "DELETE FROM statistic.fact_gate_metrics WHERE id = :id";
        return entityManager.createNativeQuery(sql)
                .setParameter("id", id)
                .executeUpdate();
    }

    public Optional<FactGateMetricsDto> findLatestByCharacterName(String characterName) {
        String sql = """
            SELECT id, raid_name, gate_number, difficulty, play_time, ts
            FROM statistic.fact_gate_metrics
            WHERE character_id = :characterName AND party_run_id IS NULL
            ORDER BY ts DESC
            LIMIT 1
            """;
        try {
            Object[] result = (Object[]) entityManager.createNativeQuery(sql, Object[].class)
                    .setParameter("characterName", characterName)
                    .getSingleResult();

            return Optional.of(FactGateMetricsDto.builder()
                    .id(((Number) result[0]).longValue())
                    .raidName((String) result[1])
                    .gateNumber(((Number) result[2]).shortValue())
                    .difficulty((String) result[3])
                    .playTime(toDuration(result[4])) // 변환 메소드 사용
                    .ts(result[5] instanceof Instant ? (Instant) result[5] : null)
                    .build());
        } catch (jakarta.persistence.NoResultException e) {
            return Optional.empty();
        }
    }

    public Optional<FactGateMetricsDto> findLatestByCharacterNameAndRaidName(String characterName, String raidName) {
        String sql = """
            SELECT id, raid_name, gate_number, difficulty, play_time, ts
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
                    .id(((Number) result[0]).longValue())
                    .raidName((String) result[1])
                    .gateNumber(((Number) result[2]).shortValue())
                    .difficulty((String) result[3])
                    .playTime(toDuration(result[4])) // 변환 메소드 사용
                    .ts(result[5] instanceof Instant ? (Instant) result[5] : null)
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
                id, raid_name, gate_number, difficulty, play_time,
                character_id, class_name, role, dps, synergy_efficiency_rate,
                back_attack_rate, head_attack_rate, crit_rate, total_damage,
                support_dps, support_attack_buff_effectiveness_rate,
                support_brand_buff_effectiveness_rate, support_damage_buff2_effectiveness_rate,
                support_damage_increase_effectiveness_rate, support_assist_total_damage,
                counter_success, stagger_damage, party_heal_amount
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
                        .id(row[0] != null ? ((Number) row[0]).longValue() : null)
                        .raidName((String) row[1])
                        .gateNumber(row[2] != null ? ((Number) row[2]).shortValue() : null)
                        .difficulty((String) row[3])
                        .playTime(toDuration(row[4])) // 변환 메소드 사용
                        .characterId((String) row[5])
                        .className((String) row[6])
                        .role((String) row[7])
                        .dps(row[8] != null ? ((Number) row[8]).doubleValue() : null)
                        .synergyEfficiencyRate(row[9] != null ? ((Number) row[9]).doubleValue() : null)
                        .backAttackRate(row[10] != null ? ((Number) row[10]).doubleValue() : null)
                        .headAttackRate(row[11] != null ? ((Number) row[11]).doubleValue() : null)
                        .critRate(row[12] != null ? ((Number) row[12]).doubleValue() : null)
                        .totalDamage(row[13] != null ? new BigDecimal(row[13].toString()) : null)
                        .supportDps(row[14] != null ? ((Number) row[14]).doubleValue() : null)
                        .supportAttackBuffEffectivenessRate(row[15] != null ? ((Number) row[15]).doubleValue() : null)
                        .supportBrandBuffEffectivenessRate(row[16] != null ? ((Number) row[16]).doubleValue() : null)
                        .supportDamageBuff2EffectivenessRate(row[17] != null ? ((Number) row[17]).doubleValue() : null)
                        .supportDamageIncreaseEffectivenessRate(row[18] != null ? ((Number) row[18]).doubleValue() : null)
                        .supportAssistTotalDamage(row[19] != null ? new BigDecimal(row[19].toString()) : null)
                        .counterSuccess(toLongList(row[20]))
                        .staggerDamage(toLongList(row[21]))
                        .partyHealAmount(toDoubleList(row[22]))
                        .build())
                .collect(Collectors.toList());
    }

    public List<FactGateMetricsDto> findAllByCharacterName(String characterName) {
        String sql = """
            SELECT
                id, raid_name, gate_number, difficulty, play_time,
                character_id, class_name, role, dps, synergy_efficiency_rate,
                back_attack_rate, head_attack_rate, crit_rate, total_damage,
                support_dps, support_attack_buff_effectiveness_rate,
                support_brand_buff_effectiveness_rate, support_damage_buff2_effectiveness_rate,
                support_damage_increase_effectiveness_rate, support_assist_total_damage,
                counter_success, stagger_damage, party_heal_amount
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
                        .id(row[0] != null ? ((Number) row[0]).longValue() : null)
                        .raidName((String) row[1])
                        .gateNumber(row[2] != null ? ((Number) row[2]).shortValue() : null)
                        .difficulty((String) row[3])
                        .playTime(toDuration(row[4]))
                        .characterId((String) row[5])
                        .className((String) row[6])
                        .role((String) row[7])
                        .dps(row[8] != null ? ((Number) row[8]).doubleValue() : null)
                        .synergyEfficiencyRate(row[9] != null ? ((Number) row[9]).doubleValue() : null)
                        .backAttackRate(row[10] != null ? ((Number) row[10]).doubleValue() : null)
                        .headAttackRate(row[11] != null ? ((Number) row[11]).doubleValue() : null)
                        .critRate(row[12] != null ? ((Number) row[12]).doubleValue() : null)
                        .totalDamage(row[13] != null ? new BigDecimal(row[13].toString()) : null)
                        .supportDps(row[14] != null ? ((Number) row[14]).doubleValue() : null)
                        .supportAttackBuffEffectivenessRate(row[15] != null ? ((Number) row[15]).doubleValue() : null)
                        .supportBrandBuffEffectivenessRate(row[16] != null ? ((Number) row[16]).doubleValue() : null)
                        .supportDamageBuff2EffectivenessRate(row[17] != null ? ((Number) row[17]).doubleValue() : null)
                        .supportDamageIncreaseEffectivenessRate(row[18] != null ? ((Number) row[18]).doubleValue() : null)
                        .supportAssistTotalDamage(row[19] != null ? new BigDecimal(row[19].toString()) : null)
                        .counterSuccess(toLongList(row[20]))
                        .staggerDamage(toLongList(row[21]))
                        .partyHealAmount(toDoubleList(row[22]))
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

    private List<Long> toLongList(Object obj) {
        if (obj == null) return null;
        try {
            if (obj instanceof Array) {
                Object array = ((Array) obj).getArray();
                if (array instanceof Long[]) {
                    return Arrays.asList((Long[]) array);
                } else if (array instanceof long[]) { // For primitive arrays
                    return Arrays.stream((long[]) array).boxed().collect(Collectors.toList());
                } else if (array instanceof Integer[]) {
                    return Arrays.stream((Integer[]) array).map(Integer::longValue).collect(Collectors.toList());
                }
            }
        } catch (SQLException e) {
            // Log this exception
            return null;
        }
        return null;
    }

    private List<Double> toDoubleList(Object obj) {
        if (obj == null) return null;
        try {
            if (obj instanceof Array) {
                Object array = ((Array) obj).getArray();
                if (array instanceof Double[]) {
                    return Arrays.asList((Double[]) array);
                } else if (array instanceof double[]) { // For primitive arrays
                    return Arrays.stream((double[]) array).boxed().collect(Collectors.toList());
                }
            }
        } catch (SQLException e) {
            // Log this exception
            return null;
        }
        return null;
    }
}
