package com.example.Loark.DTO;

import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CharacterResponse {
    private Long characterId;
    private String name;
    private String server;
    private String clazz;
    private Integer level;
    private Integer expeditionLevel;
    private BigDecimal itemLevel;
    private Long combatPower;
    private boolean main;
    private LocalDateTime updatedAt;
    private String arkPassive;
}