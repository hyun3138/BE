package com.example.Loark.Service;

import com.example.Loark.Repository.PartyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class PartyAuthz {
    private final PartyRepository parties;

    /** partyId의 owner가 meId인지 여부 */
    public boolean isOwner(UUID partyId, Long meId) {
        return parties.existsByPartyIdAndOwner_UserId(partyId, meId);
    }

    /** 가시성 문자열 검증: "private" | "public" 만 허용 */
    public void validateVisibility(String visibility) {
        if (!"private".equals(visibility) && !"public".equals(visibility)) {
            throw new IllegalArgumentException("visibility must be 'private' or 'public'");
        }
    }
}
