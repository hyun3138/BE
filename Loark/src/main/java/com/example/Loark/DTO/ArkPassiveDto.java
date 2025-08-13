package com.example.Loark.DTO;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ArkPassiveDto {
    @JsonProperty("ArkPassive")
    private ArkPassive arkPassive;
    public ArkPassive getArkPassive() { return arkPassive; }

    public static class ArkPassive {
        @JsonProperty("Title")
        private String title;
        @JsonProperty("IsArkPassive")
        private Boolean isArkPassive;

        public String getTitle() { return title; }
        public Boolean getIsArkPassive() { return isArkPassive; }
    }
}