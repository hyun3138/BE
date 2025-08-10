package com.example.Loark.DTO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class DefaultResponse<T> {
    private int statusCode;
    private String responseMessage;
    private T data;
}
