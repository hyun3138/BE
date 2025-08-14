package com.example.Loark.DTO;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class Character_ArkPassive {

    @JsonProperty("ArkPassive")
    private ArkPassive arkPassive;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ArkPassive {
        @JsonProperty("Title")
        private String title;          // 예) "질풍노도"
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Point {
        @JsonProperty("Value")
        private Integer value;
        @JsonProperty("Tooltip")
        private List<String> tooltip;
    }
}