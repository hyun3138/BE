package com.example.Loark.DTO;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class Character_Profile {
    @JsonProperty("ServerName")
    private String ServerName;

    @JsonProperty("CharacterName")
    private String CharacterName;

    @JsonProperty("CharacterClassName")
    private String CharacterClassName;

    @JsonProperty("CharacterLevel")
    private Integer CharacterLevel;        // 전투 레벨

    @JsonProperty("ExpeditionLevel")
    private Integer ExpeditionLevel;       // 원정대 레벨

    @JsonProperty("ItemAvgLevel")
    private String ItemAvgLevel;

    @JsonProperty("CombatPower")
    private String CombatPower;
    
    // db에 저장 안하고 값만 뿌릴 수 있음
//    private List<Stat> Stats;
//    @Data public static class Stat {
//        private String Type;   // "치명" 등
//        private String Value;  // "561"
//        private List<String> Tooltip;
//    }
}
