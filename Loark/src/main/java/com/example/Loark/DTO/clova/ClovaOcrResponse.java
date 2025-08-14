package com.example.Loark.DTO.clova;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ClovaOcrResponse {
    private List<Image> images;
    private String requestId;
    private String version;
    private long timestamp;
}