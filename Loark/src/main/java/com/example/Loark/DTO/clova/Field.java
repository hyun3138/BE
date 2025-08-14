package com.example.Loark.DTO.clova;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class Field {
    private String name;
    private String inferText;
    private boolean isTable;
}