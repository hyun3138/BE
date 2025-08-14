package com.example.Loark.Service;


import com.example.Loark.DTO.Character_Profile;
import com.example.Loark.Entity.Character;
import com.example.Loark.Entity.User;
import com.example.Loark.Repository.CharacterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
@RequiredArgsConstructor
public class CharacterService {
    private final LostarkApiClient loa;
    private final CharacterRepository characterRepo;

    @Transactional
    public Character upsertFromProfileWithArkPassive(User user, String characterName, boolean setAsMain) {
        if (user.getUserApiKey() == null || user.getUserApiKey().isBlank())
            throw new IllegalStateException("API Key가 필요합니다.");

        // 1) 프로필
        var p = loa.fetchProfile(user.getUserApiKey(), characterName);

        Character ch = characterRepo.findByUserAndName(user, p.getCharacterName())
                .orElse(Character.builder().user(user).name(p.getCharacterName()).build());

        ch.setServer(p.getServerName());
        ch.setClazz(p.getCharacterClassName());
        ch.setLevel(safeInt(p.getCharacterLevel()));
        ch.setExpeditionLevel(safeInt(p.getExpeditionLevel()));
        ch.setItemLevel(parseItemLevel(p.getItemAvgLevel()));
        ch.setCombatPower(parseCombatPowerToLong(p.getCombatPower()));

        // 2) 아크패시브(직업 각인)
        String arkTitle = loa.fetchArkPassiveTitle(user.getUserApiKey(), p.getCharacterName());
        if (arkTitle != null && !arkTitle.isBlank()) {
            ch.setArkPassive(arkTitle);       // 예) "질풍노도"
        } else {
            ch.setArkPassive(null);           // 없으면 null로 정리
        }

        // 3) 대표 토글
        if (setAsMain) {
            characterRepo.findByUserAndMainTrue(user).ifPresent(prev -> {
                if (!prev.getName().equalsIgnoreCase(ch.getName())) {
                    prev.setMain(false);
                    characterRepo.save(prev);
                }
            });
            ch.setMain(true);
            // 필요시 user.setMainCharacter(ch.getName()) 동기화는 컨트롤러에서 이미 수행 중
        }

        return characterRepo.save(ch);
    }


    private static Integer safeInt(Integer v) { return (v == null ? 0 : v); }

    private static BigDecimal parseItemLevel(String raw) {
        if (raw == null || raw.isBlank()) return new BigDecimal("0.00");
        return new BigDecimal(raw.replace(",", "")); // "1,703.33" -> 1703.33
    }

    // CombatPower "1,689.13" → Long(반올림). 스키마가 Long이라 안전하게 반올림 저장.
    private static Long parseCombatPowerToLong(String raw) {
        if (raw == null || raw.isBlank()) return null;
        var bd = new BigDecimal(raw.replace(",", "")); // 1689.13
        return bd.setScale(0, RoundingMode.HALF_UP).longValueExact(); // 1689
    }

}
