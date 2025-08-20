package com.example.Loark.DTO;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public class HistogramDto {

    @JsonProperty("x_bins")
    private List<Integer> xBins;

    @JsonProperty("y_counts")
    private List<Integer> yCounts;

    public List<Integer> getXBins() {
        return xBins;
    }

    public List<Integer> getYCounts() {
        return yCounts;
    }
}
