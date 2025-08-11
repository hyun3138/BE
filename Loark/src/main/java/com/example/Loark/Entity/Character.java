package com.example.Loark.Entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name="characters",
        uniqueConstraints = @UniqueConstraint(name="ux_characters_user_name",
                columnNames={"user_id","character_name"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Character {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name="character_id")
    private Long characterId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name="user_id", nullable=false)
    private User user;

    @Column(name="character_name", nullable=false, length=100)
    private String name;

    @Column(name="character_server", nullable=false, length=100)
    private String server;

    @Column(name="character_class", nullable=false, length=50)
    private String clazz;

    @Column(name="character_level", nullable=false)
    private Integer level;

    @Column(name="character_expedition_level", nullable=false)
    private Integer expeditionLevel;

    @Column(name="character_item_level", nullable=false, precision=6, scale=2)
    private BigDecimal itemLevel;

    @Column(name="character_combat_power")
    private Long combatPower;

    @Column(name="updated_at", nullable=false)
    private LocalDateTime updatedAt;

    @Column(name="is_main", nullable=false)
    private boolean main;

    @PrePersist @PreUpdate
    void touch() { if (updatedAt == null) updatedAt = LocalDateTime.now(); else updatedAt = LocalDateTime.now(); }
}
