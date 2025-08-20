package com.example.Loark.Entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "character_specs")
public class CharacterSpec {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "spec_id")
    private Long specId;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "character_id", nullable = false)
    private Character character;

    @Column(name="character_item_level", precision=6, scale=2)
    private BigDecimal itemLevel;

    @Column(name="character_combat_power")
    private Long combatPower;

    @Column(name="character_ark_passive", length=50)
    private String arkPassive;

    // 장비(Equip)
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "equip_helmet", columnDefinition = "jsonb")
    private String equipHelmet;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "equip_shoulders", columnDefinition = "jsonb")
    private String equipShoulders;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "equip_chest", columnDefinition = "jsonb")
    private String equipChest;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "equip_legs", columnDefinition = "jsonb")
    private String equipLegs;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "equip_gloves", columnDefinition = "jsonb")
    private String equipGloves;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "equip_weapon", columnDefinition = "jsonb")
    private String equipWeapon;

    // 장신구(Accessory)
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "acc_earring1", columnDefinition = "jsonb")
    private String accEarring1;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "acc_earring2", columnDefinition = "jsonb")
    private String accEarring2;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "acc_ring1", columnDefinition = "jsonb")
    private String accRing1;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "acc_ring2", columnDefinition = "jsonb")
    private String accRing2;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "acc_necklace", columnDefinition = "jsonb")
    private String accNecklace;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "acc_bracelet", columnDefinition = "jsonb")
    private String accBracelet;

    // 어빌리티 스톤
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "ability_stone", columnDefinition = "jsonb")
    private String abilityStone;

    // 카드
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "card_1", columnDefinition = "jsonb")
    private String card1;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "card_2", columnDefinition = "jsonb")
    private String card2;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "card_3", columnDefinition = "jsonb")
    private String card3;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "card_4", columnDefinition = "jsonb")
    private String card4;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "card_5", columnDefinition = "jsonb")
    private String card5;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "card_6", columnDefinition = "jsonb")
    private String card6;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "card_effect", columnDefinition = "jsonb")
    private String cardEffect;

    // 각인
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "engraving_1", columnDefinition = "jsonb")
    private String engraving1;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "engraving_2", columnDefinition = "jsonb")
    private String engraving2;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "engraving_3", columnDefinition = "jsonb")
    private String engraving3;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "engraving_4", columnDefinition = "jsonb")
    private String engraving4;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "engraving_5", columnDefinition = "jsonb")
    private String engraving5;

    // 아크 패시브(요약 + 상세)
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "ark_evolution", columnDefinition = "jsonb")
    private String arkEvolution;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "ark_realization", columnDefinition = "jsonb")
    private String arkRealization;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "ark_leap", columnDefinition = "jsonb")
    private String arkLeap;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "ark_evolution_detail", columnDefinition = "jsonb")
    private String arkEvolutionDetail;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "ark_realization_detail", columnDefinition = "jsonb")
    private String arkRealizationDetail;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "ark_leap_detail", columnDefinition = "jsonb")
    private String arkLeapDetail;

    // 보석(최대 11개)
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "gem_1", columnDefinition = "jsonb")
    private String gem1;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "gem_2", columnDefinition = "jsonb")
    private String gem2;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "gem_3", columnDefinition = "jsonb")
    private String gem3;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "gem_4", columnDefinition = "jsonb")
    private String gem4;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "gem_5", columnDefinition = "jsonb")
    private String gem5;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "gem_6", columnDefinition = "jsonb")
    private String gem6;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "gem_7", columnDefinition = "jsonb")
    private String gem7;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "gem_8", columnDefinition = "jsonb")
    private String gem8;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "gem_9", columnDefinition = "jsonb")
    private String gem9;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "gem_10", columnDefinition = "jsonb")
    private String gem10;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "gem_11", columnDefinition = "jsonb")
    private String gem11;

    // 스킬(대표 8개)
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "skill_1", columnDefinition = "jsonb")
    private String skill1;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "skill_2", columnDefinition = "jsonb")
    private String skill2;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "skill_3", columnDefinition = "jsonb")
    private String skill3;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "skill_4", columnDefinition = "jsonb")
    private String skill4;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "skill_5", columnDefinition = "jsonb")
    private String skill5;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "skill_6", columnDefinition = "jsonb")
    private String skill6;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "skill_7", columnDefinition = "jsonb")
    private String skill7;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "skill_8", columnDefinition = "jsonb")
    private String skill8;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
