package com.example.Loark.DTO.clova;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class Image {
    private MatchedTemplate matchedTemplate;
    private List<Field> fields;
    private String uid;
    private String name;
    private String inferResult;
    private String message;
}