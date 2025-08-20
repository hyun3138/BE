package com.example.Loark.DTO;

import com.fasterxml.jackson.annotation.JsonProperty;

public class BoxplotDto {

    @JsonProperty("bin")
    private Integer bin;

    @JsonProperty("min")
    private Double min;

    @JsonProperty("q1")
    private Double q1;

    @JsonProperty("median")
    private Double median;

    @JsonProperty("q3")
    private Double q3;

    @JsonProperty("max")
    private Double max;

    public Integer getBin() {
        return bin;
    }

    public Double getMin() {
        return min;
    }

    public Double getQ1() {
        return q1;
    }

    public Double getMedian() {
        return median;
    }

    public Double getQ3() {
        return q3;
    }

    public Double getMax() {
        return max;
    }
}
