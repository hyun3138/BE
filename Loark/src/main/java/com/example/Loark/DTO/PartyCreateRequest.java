package com.example.Loark.DTO;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
public class PartyCreateRequest {
    private String name;
    private String visibility; // "private" | "public"
}
