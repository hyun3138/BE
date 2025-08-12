package com.example.Loark.DTO;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class LoaSiblings {
    @JsonProperty("ServerName")
    private String serverName;

    @JsonProperty("CharacterName")
    private String characterName;

    @JsonProperty("CharacterClassName")
    private String characterClassName;

    @JsonProperty("ItemAvgLevel")
    private String itemAvgLevel;

    @JsonProperty("CharacterLevel")
    private Integer CharacterLevel;
}
