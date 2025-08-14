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

    /** ✅ 저장 전용: 이미 있으면 예외(또는 false 반환하도록 바꿔도 됨) */
    @Transactional
    public Character insertOnlyFromProfileWithArkPassive(User user, String characterName, boolean setAsMain) {
        requireApiKey(user);

        // 이미 있으면 → 저장 금지(중복 처리)
        if (characterRepo.existsByUserAndName(user, characterName)) {
            throw new IllegalStateException("이미 저장된 캐릭터입니다: " + characterName);
        }

        var p = loa.fetchProfile(user.getUserApiKey(), characterName);
        var ch = Character.builder()
                .user(user)
                .name(p.getCharacterName())
                .server(p.getServerName())
                .clazz(p.getCharacterClassName())
                .level(safeInt(p.getCharacterLevel()))
                .expeditionLevel(safeInt(p.getExpeditionLevel()))
                .itemLevel(parseItemLevel(p.getItemAvgLevel()))
                .combatPower(parseCombatPowerToLong(p.getCombatPower()))
                .main(false) // 아래에서 setAsMain 처리
                .build();

        String arkTitle = loa.fetchArkPassiveTitle(user.getUserApiKey(), p.getCharacterName());
        ch.setArkPassive((arkTitle != null && !arkTitle.isBlank()) ? arkTitle : null);

        if (setAsMain) {
            characterRepo.findByUserAndMainTrue(user).ifPresent(prev -> {
                if (!prev.getName().equalsIgnoreCase(ch.getName())) {
                    prev.setMain(false);
                    characterRepo.save(prev);
                }
            });
            ch.setMain(true);
        }

        return characterRepo.save(ch);
    }
    /** ✅ 갱신 전용: 이미 존재하는 캐릭터만, “상향일 때만” 필드 갱신 */
    @Transactional
    public boolean refreshIfHigher(User user, String characterName) {
        requireApiKey(user);

        var opt = characterRepo.findByUserAndName(user, characterName);
        if (opt.isEmpty()) return false; // 저장되지 않은 캐릭터는 갱신 대상 아님

        var current = opt.get();
        var p = loa.fetchProfile(user.getUserApiKey(), characterName);

        var newItemLevel   = parseItemLevel(p.getItemAvgLevel());
        var newCombatPower = parseCombatPowerToLong(p.getCombatPower());

        // 비교 기준: 둘 중 하나라도 "상향"이면 업데이트
        boolean higher =
                (newItemLevel != null && newItemLevel.compareTo(nvl(current.getItemLevel())) > 0) ||
                        (newCombatPower != null && nvl(current.getCombatPower()) != null &&
                                newCombatPower.compareTo(nvl(current.getCombatPower())) > 0);

        if (!higher) return false; // 하향/동일 => 저장 금지

        // 상향이면 주요 프로필 동기화
        current.setServer(p.getServerName());
        current.setClazz(p.getCharacterClassName());
        current.setLevel(safeInt(p.getCharacterLevel()));
        current.setExpeditionLevel(safeInt(p.getExpeditionLevel()));
        current.setItemLevel(newItemLevel);
        current.setCombatPower(newCombatPower);

        // (선택) 아크패시브도 최신으로 보정
        String arkTitle = loa.fetchArkPassiveTitle(user.getUserApiKey(), p.getCharacterName());
        current.setArkPassive((arkTitle != null && !arkTitle.isBlank()) ? arkTitle : null);

        characterRepo.save(current); // @PreUpdate로 updated_at 갱신
        return true;
    }

    /** ✅ 내 캐릭터 전부 갱신(상향인 것만) */
    @Transactional
    public RefreshSummary refreshAllHigher(User user) {
        var myChars = characterRepo.findAllByUser(user); // DB에 “이미 저장된” 캐릭터 목록
        int updated = 0, skipped = 0, error = 0;

        for (var ch : myChars) {
            try {
                boolean ok = refreshIfHigher(user, ch.getName());
                if (ok) updated++; else skipped++;
            } catch (Exception e) {
                error++;
            }
        }
        return new RefreshSummary(updated, skipped, error);
    }


    private static Integer safeInt(Integer v) { return (v == null ? 0 : v); }
    private static java.math.BigDecimal parseItemLevel(String raw) {
        if (raw == null || raw.isBlank()) return new java.math.BigDecimal("0.00");
        return new java.math.BigDecimal(raw.replace(",", ""));
    }
    private static Long parseCombatPowerToLong(String raw) {
        if (raw == null || raw.isBlank()) return null;
        var bd = new java.math.BigDecimal(raw.replace(",", ""));
        return bd.setScale(0, java.math.RoundingMode.HALF_UP).longValueExact();
    }
    private static void requireApiKey(User user) {
        if (user.getUserApiKey() == null || user.getUserApiKey().isBlank())
            throw new IllegalStateException("API Key가 필요합니다.");
    }
    private static java.math.BigDecimal nvl(java.math.BigDecimal v) { return v == null ? new java.math.BigDecimal("0.00") : v; }
    private static Long nvl(Long v) { return v == null ? 0L : v; }

    @lombok.Value
    public static class RefreshSummary {
        int updated;
        int skipped; // 하향/동일이라 스킵
        int error;   // API 실패 등
    }
}
