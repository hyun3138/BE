package com.example.Loark.DTO;

import com.fasterxml.jackson.annotation.JsonProperty;

public class PercentileDto {

    @JsonProperty("percentile")
    private Double percentile;

    public Double getPercentile() {
        return percentile;
    }
}
