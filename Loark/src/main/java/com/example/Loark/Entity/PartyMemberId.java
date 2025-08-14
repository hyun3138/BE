package com.example.Loark.Entity;

import jakarta.persistence.Embeddable;
import lombok.*;
import java.io.Serializable;
import java.util.UUID;

@Embeddable
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @EqualsAndHashCode
public class PartyMemberId {
    private UUID partyId;
    private Long userId;
}
