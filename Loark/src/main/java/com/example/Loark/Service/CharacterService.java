package com.example.Loark.Service;

import com.example.Loark.Entity.CharacterSpec;
import com.example.Loark.Repository.CharacterSpecRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.Loark.DTO.Character_Profile;
import com.example.Loark.Entity.Character;
import com.example.Loark.Entity.User;
import com.example.Loark.Repository.CharacterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CharacterService {
    private final LostarkApiClient loa;
    private final CharacterRepository characterRepo;
    private final CharacterSpecRepository characterSpecRepo;
    private final ObjectMapper mapper;

    @Transactional(readOnly = true)
    public Optional<CharacterSpec> getLatestCharacterSpec(String characterName) {
        // 캐릭터 이름으로 캐릭터 정보를 찾습니다. (User 정보 없이 조회, 동명 캐릭터가 없다고 가정)
        Optional<Character> characterOpt = characterRepo.findByName(characterName);
        if (characterOpt.isEmpty()) {
            return Optional.empty(); // 캐릭터가 없으면 빈 Optional 반환
        }

        // 해당 캐릭터의 가장 최신 스펙을 조회합니다.
        return characterSpecRepo.findFirstByCharacterCharacterIdOrderByUpdatedAtDesc(characterOpt.get().getCharacterId());
    }

    @Transactional(readOnly = true)
    public Optional<CharacterSpec> getCharacterSpecByDate(String characterName, LocalDate date) {
        // 캐릭터 이름으로 캐릭터 정보를 찾습니다.
        Optional<Character> characterOpt = characterRepo.findByName(characterName);
        if (characterOpt.isEmpty()) {
            return Optional.empty();
        }

        // 해당 날짜의 끝 시간(23:59:59)을 기준으로 그 이전에 저장된 가장 최신 스펙을 조회합니다.
        LocalDateTime endOfDay = date.atTime(LocalTime.MAX);
        return characterSpecRepo.findFirstByCharacterCharacterIdAndUpdatedAtLessThanEqualOrderByUpdatedAtDesc(
                characterOpt.get().getCharacterId(),
                endOfDay
        );
    }

    @Transactional
    public void saveCharacterSpec(User user, String characterName) throws JsonProcessingException {
        requireApiKey(user);

        Character character = characterRepo.findByUserAndName(user, characterName)
                .orElseThrow(() -> new IllegalArgumentException("캐릭터를 찾을 수 없습니다: " + characterName));

        String armoryJson = loa.fetchArmory(user.getUserApiKey(), characterName);
        if (armoryJson == null) {
            return; // Or handle error appropriately
        }

        JsonNode root = mapper.readTree(armoryJson);

        // API 응답이 배열인 경우, 첫 번째 요소를 사용
        if (root.isArray()) {
            if (root.size() > 0) {
                root = root.get(0);
            } else {
                // 배열이 비어있는 경우 처리 (예: 캐릭터 정보 없음)
                return;
            }
        }

        // 항상 새로운 CharacterSpec 객체를 생성하여 이력으로 저장
        CharacterSpec spec = new CharacterSpec();
        spec.setCharacter(character);

        // 프로필 정보에서 아이템 레벨과 전투력 추출 및 저장
        JsonNode profile = root.path("ArmoryProfile");
        spec.setItemLevel(parseItemLevel(profile.path("ItemAvgLevel").asText(null)));
        spec.setCombatPower(parseCombatPowerToLong(profile.path("CombatPower").asText(null)));

        // To handle unequipped items, null out all fields before populating.
        spec.setEquipHelmet(null);
        spec.setEquipShoulders(null);
        spec.setEquipChest(null);
        spec.setEquipLegs(null);
        spec.setEquipGloves(null);
        spec.setEquipWeapon(null);
        spec.setAccNecklace(null);
        spec.setAccEarring1(null);
        spec.setAccEarring2(null);
        spec.setAccRing1(null);
        spec.setAccRing2(null);
        spec.setAccBracelet(null);
        spec.setAbilityStone(null);

        // 장비 & 장신구
        JsonNode equipment = root.path("ArmoryEquipment");
        if (equipment.isArray()) {
            for (JsonNode item : equipment) {
                String type = item.path("Type").asText();
                String json = getJsonNodeAsString(item);
                if (json == null) continue;

                switch (type) {
                    case "투구": spec.setEquipHelmet(json); break;
                    case "어깨": spec.setEquipShoulders(json); break;
                    case "상의": spec.setEquipChest(json); break;
                    case "하의": spec.setEquipLegs(json); break;
                    case "장갑": spec.setEquipGloves(json); break;
                    case "무기": spec.setEquipWeapon(json); break;
                    case "목걸이": spec.setAccNecklace(json); break;
                    case "귀걸이":
                        if (spec.getAccEarring1() == null) spec.setAccEarring1(json);
                        else spec.setAccEarring2(json);
                        break;
                    case "반지":
                        if (spec.getAccRing1() == null) spec.setAccRing1(json);
                        else spec.setAccRing2(json);
                        break;
                    case "팔찌": spec.setAccBracelet(json); break;
                    case "어빌리티 스톤": spec.setAbilityStone(json); break;
                }
            }
        }

        // 각인 (FIXED: Effects -> ArkPassiveEffects)
        JsonNode engravings = root.path("ArmoryEngraving").path("ArkPassiveEffects");
        for (int i = 0; i < 5; i++) {
            setEngraving(spec, i + 1, getJsonNodeAsString(engravings.path(i)));
        }

        // 보석
        JsonNode gems = root.path("ArmoryGem").path("Gems");
        for (int i = 0; i < 11; i++) {
            setGem(spec, i + 1, getJsonNodeAsString(gems.path(i)));
        }

        // 스킬 (진단 코드가 포함된 새로운 로직)
        System.out.println("스킬 저장을 시작합니다: 캐릭터명=" + characterName);

        // 먼저 기존 스킬 정보를 초기화합니다.
        for (int i = 1; i <= 8; i++) {
            setSkill(spec, i, null);
        }

        // API 응답에서 모든 스킬 목록을 가져옵니다.
        JsonNode allSkills = root.path("ArmorySkills");
        if (allSkills.isMissingNode() || !allSkills.isArray()) {
            System.out.println("### 오류: API 응답에 ArmorySkills 필드가 없거나 배열이 아닙니다!");
        } else {
            System.out.println("정보: API에서 총 " + allSkills.size() + "개의 스킬을 받아왔습니다.");
            List<String> filteredSkills = new ArrayList<>();

            for (JsonNode skill : allSkills) {
                String skillName = skill.path("Name").asText("알 수 없는 스킬");
                System.out.println("정보: 스킬 '" + skillName + "' 처리 중...");

                // 1. 각성기 필터링
                if (skill.path("SkillType").asInt(0) >= 100) {
                    System.out.println("  -> 경고: 스킬 '" + skillName + "'은(는) 각성기이므로 건너뜁니다.");
                    continue;
                }

                // 2. 트라이포드 확인
                JsonNode tripods = skill.path("Tripods");
                if (!tripods.isArray() || tripods.size() == 0) {
                    System.out.println("  -> 경고: 스킬 '" + skillName + "'은(는) 선택된 트라이포드가 없으므로 건너뜁니다.");
                    continue;
                }

                System.out.println("  -> 성공: 스킬 '" + skillName + "'을(를) 대표 스킬로 선정! (트라이포드 " + tripods.size() + "개)");
                String skillJson = getJsonNodeAsString(skill);
                if (skillJson != null) {
                    filteredSkills.add(skillJson);
                } else {
                    System.out.println("  -> ### 오류: 스킬 '" + skillName + "'을(를) JSON 문자열로 변환하는 데 실패했습니다.");
                }
            }

            System.out.println("정보: 최종적으로 " + filteredSkills.size() + "개의 대표 스킬이 선정되었습니다.");
            if (filteredSkills.isEmpty() && allSkills.size() > 0) {
                System.out.println("### 오류: 선정된 대표 스킬이 하나도 없어 null로 저장됩니다. 필터링 로직 또는 API 응답을 점검해야 합니다.");
            }

            // 필터링된 대표 스킬을 최대 8개까지 저장합니다.
            for (int i = 0; i < Math.min(filteredSkills.size(), 8); i++) {
                setSkill(spec, i + 1, filteredSkills.get(i));
            }
        }


        // 카드
        JsonNode cardsNode = root.path("ArmoryCard");
        for (int i = 0; i < 6; i++) {
            setCard(spec, i + 1, getJsonNodeAsString(cardsNode.path("Cards").path(i)));
        }
        spec.setCardEffect(getJsonNodeAsString(cardsNode.path("Effects")));

        // 아크패시브
        JsonNode arkPassiveNode = root.path("ArkPassive");

        // ✅ 직업 각인 (Ark Passive Title)
        spec.setArkPassive(arkPassiveNode.path("Title").asText(null));

        // 'Points' for summary (e.g., "진화", "깨달음", "도약" level descriptions)
        spec.setArkEvolution(null);
        spec.setArkRealization(null);
        spec.setArkLeap(null);
        JsonNode points = arkPassiveNode.path("Points");
        if (points.isArray()) {
            for (JsonNode point : points) {
                String name = point.path("Name").asText();
                switch (name) {
                    case "진화": spec.setArkEvolution(getJsonNodeAsString(point)); break;
                    case "깨달음": spec.setArkRealization(getJsonNodeAsString(point)); break;
                    case "도약": spec.setArkLeap(getJsonNodeAsString(point)); break;
                }
            }
        }

        // 'Effects' for detailed descriptions (FIXED: to produce valid JSON array string)
        spec.setArkEvolutionDetail(null);
        spec.setArkRealizationDetail(null);
        spec.setArkLeapDetail(null);
        JsonNode arkEffects = arkPassiveNode.path("Effects");
        if (arkEffects.isArray()) {
            List<String> evolutionDetails = new ArrayList<>();
            List<String> realizationDetails = new ArrayList<>();
            List<String> leapDetails = new ArrayList<>();

            for (JsonNode effect : arkEffects) {
                String name = effect.path("Name").asText();
                String tooltipJsonString = effect.path("ToolTip").asText();
                if (tooltipJsonString == null || tooltipJsonString.isEmpty()) {
                    continue;
                }

                try {
                    JsonNode tooltipRoot = mapper.readTree(tooltipJsonString);
                    String description = findDescriptionInTooltip(tooltipRoot);

                    if (description == null || description.isEmpty()) continue;

                    String cleanedDescription = description.replaceAll("<[^>]*>", "").replace("||<BR>", " ").trim();
                    if (cleanedDescription.isEmpty()) continue;

                    String effectTitle = effect.path("Description").asText().replaceAll("<[^>]*>", "").trim();
                    String finalDetail = effectTitle + ": " + cleanedDescription;

                    switch (name) {
                        case "진화":
                            evolutionDetails.add(finalDetail);
                            break;
                        case "깨달음":
                            realizationDetails.add(finalDetail);
                            break;
                        case "도약":
                            leapDetails.add(finalDetail);
                            break;
                    }
                } catch (JsonProcessingException e) {
                    // Log or handle the error if parsing the inner JSON fails
                }
            }

            if (!evolutionDetails.isEmpty()) {
                spec.setArkEvolutionDetail(mapper.writeValueAsString(evolutionDetails));
            }
            if (!realizationDetails.isEmpty()) {
                spec.setArkRealizationDetail(mapper.writeValueAsString(realizationDetails));
            }
            if (!leapDetails.isEmpty()) {
                spec.setArkLeapDetail(mapper.writeValueAsString(leapDetails));
            }
        }

        characterSpecRepo.save(spec);
    }

    private String findDescriptionInTooltip(JsonNode tooltipRoot) {
        // This helper method attempts to find the description from the complex tooltip JSON
        if (tooltipRoot == null) return null;

        // Path based on observation of '아크패시브.txt'
        JsonNode element = tooltipRoot.path("Element_001").path("value").path("Element_000").path("value");
        if (element.isTextual()) {
            return element.asText();
        }

        // Another possible path
        element = tooltipRoot.path("Element_002").path("value");
        if (element.isTextual()) {
            return element.asText();
        }
        // Add more robust searching logic if needed
        return null;
    }

    private String getJsonNodeAsString(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        try {
            return mapper.writeValueAsString(node);
        } catch (JsonProcessingException e) {
            // Consider adding logging here
            return null;
        }
    }

    private void setEngraving(CharacterSpec spec, int index, String value) {
        switch (index) {
            case 1: spec.setEngraving1(value); break;
            case 2: spec.setEngraving2(value); break;
            case 3: spec.setEngraving3(value); break;
            case 4: spec.setEngraving4(value); break;
            case 5: spec.setEngraving5(value); break;
        }
    }

    private void setGem(CharacterSpec spec, int index, String value) {
        switch (index) {
            case 1: spec.setGem1(value); break;
            case 2: spec.setGem2(value); break;
            case 3: spec.setGem3(value); break;
            case 4: spec.setGem4(value); break;
            case 5: spec.setGem5(value); break;
            case 6: spec.setGem6(value); break;
            case 7: spec.setGem7(value); break;
            case 8: spec.setGem8(value); break;
            case 9: spec.setGem9(value); break;
            case 10: spec.setGem10(value); break;
            case 11: spec.setGem11(value); break;
        }
    }

    private void setSkill(CharacterSpec spec, int index, String value) {
        switch (index) {
            case 1: spec.setSkill1(value); break;
            case 2: spec.setSkill2(value); break;
            case 3: spec.setSkill3(value); break;
            case 4: spec.setSkill4(value); break;
            case 5: spec.setSkill5(value); break;
            case 6: spec.setSkill6(value); break;
            case 7: spec.setSkill7(value); break;
            case 8: spec.setSkill8(value); break;
        }
    }

    private void setCard(CharacterSpec spec, int index, String value) {
        switch (index) {
            case 1: spec.setCard1(value); break;
            case 2: spec.setCard2(value); break;
            case 3: spec.setCard3(value); break;
            case 4: spec.setCard4(value); break;
            case 5: spec.setCard5(value); break;
            case 6: spec.setCard6(value); break;
        }
    }

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

        // 첫 번째 스펙 기록 생성
        CharacterSpec initialSpec = new CharacterSpec();
        initialSpec.setCharacter(ch);
        initialSpec.setItemLevel(ch.getItemLevel());
        initialSpec.setCombatPower(ch.getCombatPower());
        initialSpec.setArkPassive(ch.getArkPassive());
        ch.getSpecs().add(initialSpec);

        if(ch.getSpecs() == null) {
            ch.setSpecs(new ArrayList<>());
        }
        ch.getSpecs().add(initialSpec);

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
