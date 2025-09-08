package com.example.Loark.Service;

import com.example.Loark.DTO.FactGateMetricsDto;
import com.example.Loark.Entity.Character;
import com.example.Loark.Entity.CharacterSpec;
import com.example.Loark.Entity.User;
import com.example.Loark.Repository.CharacterRepository;
import com.example.Loark.Repository.CharacterSpecRepository;
import com.example.Loark.Repository.FactGateMetricsRepository;
import com.example.Loark.Repository.FriendRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CharacterService {
    private final LostarkApiClient loa;
    private final CharacterRepository characterRepo;
    private final CharacterSpecRepository characterSpecRepo;
    private final FactGateMetricsRepository factGateMetricsRepository;
    private final FriendRepository friendRepo; // 의존성 추가
    private final ObjectMapper mapper;
    private final ClovaOcrService clovaOcrService;
    private final S3UploadService s3UploadService;

    /**
     * 특정 캐릭터의 모든 전투 기록을 조회합니다. (본인 또는 친구만 가능)
     * @param characterName 조회할 캐릭터의 이름
     * @param currentUser 요청을 보낸 로그인된 사용자
     * @return 전투 기록 DTO 리스트
     * @throws IllegalStateException 권한이 없거나 캐릭터가 존재하지 않을 경우
     */
    @Transactional(readOnly = true)
    public List<FactGateMetricsDto> getCharacterCombatRecords(String characterName, User currentUser) {
        // 1. 먼저 캐릭터 이름으로 캐릭터 정보를 조회하여 소유주를 찾습니다.
        Character targetCharacter = characterRepo.findByName(characterName)
                .orElseThrow(() -> new IllegalStateException("존재하지 않는 캐릭터입니다."));

        User targetUser = targetCharacter.getUser();

        // 2. 권한을 확인합니다: 요청자가 본인이거나, 대상과 친구 관계인지 확인합니다.
        boolean isOwner = currentUser.getUserId().equals(targetUser.getUserId());
        boolean isFriend = false;
        if (!isOwner) {
            isFriend = friendRepo.existsAcceptedBetween(currentUser.getUserId(), targetUser.getUserId());
        }

        // 3. 본인도 아니고 친구도 아니면, 권한 없음 예외를 발생시킵니다.
        if (!isOwner && !isFriend) {
            throw new IllegalStateException("조회 권한이 없습니다.");
        }

        // 4. 권한이 확인되면, 전투 기록을 조회하여 반환합니다.
        return factGateMetricsRepository.findAllByCharacterName(characterName);
    }

    /**
     * 특정 전투 기록을 삭제합니다.
     * @param recordId 삭제할 전투 기록의 ID
     * @param currentUser 요청을 보낸 로그인된 사용자
     * @throws IllegalStateException 삭제할 기록이 없거나 권한이 없는 경우
     */
    @Transactional
    public void deleteCharacterCombatRecord(Long recordId, User currentUser) {
        // 1. ID로 전투 기록에 저장된 캐릭터 이름을 조회합니다.
        String characterName = factGateMetricsRepository.findCharacterNameById(recordId)
                .orElseThrow(() -> new IllegalStateException("삭제할 전투 기록을 찾을 수 없습니다."));

        // 2. 해당 캐릭터가 현재 로그인한 사용자의 소유인지 확인합니다. (권한 검사)
        boolean isOwner = characterRepo.existsByUserAndName(currentUser, characterName);
        if (!isOwner) {
            throw new IllegalStateException("해당 전투 기록을 삭제할 권한이 없습니다.");
        }

        // 3. 권한이 확인되면 기록을 삭제합니다.
        factGateMetricsRepository.deleteById(recordId);
    }

    @Transactional(readOnly = true)
    public Optional<CharacterSpec> getLatestCharacterSpec(String characterName) {
        Optional<Character> characterOpt = characterRepo.findByName(characterName);
        if (characterOpt.isEmpty()) {
            return Optional.empty();
        }
        return characterSpecRepo.findFirstByCharacterCharacterIdOrderByUpdatedAtDesc(characterOpt.get().getCharacterId());
    }

    @Transactional(readOnly = true)
    public Optional<CharacterSpec> getCharacterSpecByDate(String characterName, LocalDate date) {
        Optional<Character> characterOpt = characterRepo.findByName(characterName);
        if (characterOpt.isEmpty()) {
            return Optional.empty();
        }
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
            throw new IllegalStateException("로스트아크 API 조회에 실패했습니다.");
        }

        JsonNode root = mapper.readTree(armoryJson);
        if (root.isArray()) {
            if (root.size() > 0) root = root.get(0);
            else throw new IllegalStateException("API에서 비어있는 정보를 반환했습니다.");
        }

        CharacterSpec spec = new CharacterSpec();
        spec.setCharacter(character);
        populateSpecFromJson(spec, root, characterName);
        characterSpecRepo.save(spec);
    }

    @Transactional
    public Character insertOnlyFromProfileWithArkPassive(User user, String characterName, boolean setAsMain) throws JsonProcessingException {
        requireApiKey(user);

        if (characterRepo.existsByUserAndName(user, characterName)) {
            throw new IllegalStateException("이미 저장된 캐릭터입니다: " + characterName);
        }

        String armoryJson = loa.fetchArmory(user.getUserApiKey(), characterName);
        if (armoryJson == null) {
            throw new IllegalStateException("로스트아크 API에서 캐릭터 정보를 가져올 수 없습니다: " + characterName);
        }

        JsonNode root = mapper.readTree(armoryJson);
        if (root.isArray()) {
            if (root.size() > 0) root = root.get(0);
            else throw new IllegalStateException("API에서 비어있는 정보를 반환했습니다.");
        }

        JsonNode profile = root.path("ArmoryProfile");
        if (profile.isMissingNode()) {
            throw new IllegalStateException("API 응답에 ArmoryProfile이 없습니다.");
        }

        var ch = Character.builder()
                .user(user)
                .name(profile.path("CharacterName").asText())
                .clazz(profile.path("CharacterClassName").asText())
                .server(profile.path("ServerName").asText())
                .main(false)
                .build();

        ch.setSpecs(new ArrayList<>());

        CharacterSpec initialSpec = new CharacterSpec();
        initialSpec.setCharacter(ch);
        populateSpecFromJson(initialSpec, root, characterName);
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

    @Transactional
    public Map<String, String> processFileUpload(MultipartFile file, String characterName) throws IOException {
        // 1. 캐릭터 조회
        Character character = characterRepo.findByName(characterName)
                .orElseThrow(() -> new IllegalArgumentException("캐릭터를 찾을 수 없습니다: " + characterName));

        // 2. Clova OCR로 이미지 분석
        Map<String, Object> parsedData = clovaOcrService.analyzeImage(file);
        @SuppressWarnings("unchecked")
        Map<String, String> info = (Map<String, String>) parsedData.get("info");

        String recordedAtStr = info.get("recorded_at");
        if (recordedAtStr == null) {
            throw new IllegalArgumentException("이미지에서 recorded_at 정보를 추출할 수 없습니다.");
        }
        LocalDateTime recordedAt = LocalDateTime.parse(recordedAtStr, DateTimeFormatter.ofPattern("yyyy.MM.ddHH:mm:ss"));

        // 3. 시간 검증 1: 가입일보다 이전인지 확인
        CharacterSpec firstSpec = characterSpecRepo.findFirstByCharacterCharacterIdOrderByUpdatedAtAsc(character.getCharacterId())
                .orElseThrow(() -> new IllegalStateException("캐릭터의 스펙 정보가 존재하지 않습니다."));

        if (recordedAt.isBefore(firstSpec.getUpdatedAt())) {
            throw new IllegalArgumentException("가입일(" + firstSpec.getUpdatedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) + ")보다 이전의 전투분석기는 등록 불가능합니다.");
        }

        // 4. 시간 검증 2: recorded_at 이전의 가장 가까운 spec_id 찾기
        CharacterSpec targetSpec = characterSpecRepo.findFirstByCharacterCharacterIdAndUpdatedAtBeforeOrderByUpdatedAtDesc(character.getCharacterId(), recordedAt)
                .orElseThrow(() -> new IllegalArgumentException("해당 날짜(" + recordedAt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) + ") 이전에 유효한 캐릭터 스펙 정보가 없습니다."));

        Long specId = targetSpec.getSpecId();

        // 5. 파일명 생성
        String fullCharacterInfo = String.format("%s_(%s/%s/%s/%s)",
                character.getName(),
                character.getClazz(),
                targetSpec.getArkPassive() != null ? targetSpec.getArkPassive() : "N/A",
                targetSpec.getItemLevel() != null ? targetSpec.getItemLevel().toString() : "N/A",
                targetSpec.getCombatPower() != null ? targetSpec.getCombatPower().toString() : "N/A");

        String sanitizedCharacterInfo = fullCharacterInfo
                .replaceAll("\s*/\s*", "_")
                .replaceAll("[\s()]", "");

        String uploadTimestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));

        // 6. S3 키 생성
        String imageS3Key = String.format("%s_%s.png", sanitizedCharacterInfo, uploadTimestamp);

        String raidName = info.getOrDefault("raid_name", "unknown");
        String difficulty = info.getOrDefault("난이도", "unknown");
        String gate = info.getOrDefault("관문", "unknown");
        String jsonS3Key = String.format("%s_%s_%s_%s_%s.json",
                sanitizedCharacterInfo, raidName, difficulty, gate, uploadTimestamp);

        // 7. JSON 데이터에 spec_id 추가
        parsedData.put("spec_id", specId);

        // 8. S3에 업로드
        String imageUrl = s3UploadService.uploadImage(file, imageS3Key);
        String jsonUrl = s3UploadService.uploadJson(parsedData, jsonS3Key);

        // 9. 결과 반환
        Map<String, String> successResult = new HashMap<>();
        successResult.put("original_filename", file.getOriginalFilename());
        successResult.put("image_url", imageUrl);
        successResult.put("json_url", jsonUrl);
        return successResult;
    }

    private void populateSpecFromJson(CharacterSpec spec, JsonNode root, String characterName) throws JsonProcessingException {
        JsonNode profile = root.path("ArmoryProfile");
        spec.setItemLevel(parseItemLevel(profile.path("ItemAvgLevel").asText(null)));
        spec.setCombatPower(parseCombatPowerToLong(profile.path("CombatPower").asText(null)));

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

        JsonNode engravings = root.path("ArmoryEngraving").path("ArkPassiveEffects");
        for (int i = 0; i < 5; i++) {
            setEngraving(spec, i + 1, getJsonNodeAsString(engravings.path(i)));
        }

        JsonNode gems = root.path("ArmoryGem").path("Gems");
        for (int i = 0; i < 11; i++) {
            setGem(spec, i + 1, getJsonNodeAsString(gems.path(i)));
        }

        for (int i = 1; i <= 8; i++) {
            setSkill(spec, i, null);
        }

        JsonNode allSkills = root.path("ArmorySkills");
        if (allSkills.isArray()) {
            List<String> filteredSkills = new ArrayList<>();
            for (JsonNode skill : allSkills) {
                if (skill.path("SkillType").asInt(0) >= 100) continue;
                JsonNode tripods = skill.path("Tripods");
                if (!tripods.isArray() || tripods.size() == 0) continue;
                String skillJson = getJsonNodeAsString(skill);
                if (skillJson != null) {
                    filteredSkills.add(skillJson);
                }
            }
            for (int i = 0; i < Math.min(filteredSkills.size(), 8); i++) {
                setSkill(spec, i + 1, filteredSkills.get(i));
            }
        }

        JsonNode cardsNode = root.path("ArmoryCard");
        for (int i = 0; i < 6; i++) {
            setCard(spec, i + 1, getJsonNodeAsString(cardsNode.path("Cards").path(i)));
        }
        spec.setCardEffect(getJsonNodeAsString(cardsNode.path("Effects")));

        JsonNode arkPassiveNode = root.path("ArkPassive");
        spec.setArkPassive(arkPassiveNode.path("Title").asText(null));

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
                if (tooltipJsonString == null || tooltipJsonString.isEmpty()) continue;

                try {
                    JsonNode tooltipRoot = mapper.readTree(tooltipJsonString);
                    String description = findDescriptionInTooltip(tooltipRoot);
                    if (description == null || description.isEmpty()) continue;

                    String cleanedDescription = description.replaceAll("<[^>]*>", "").replace("||<BR>", " ").trim();
                    if (cleanedDescription.isEmpty()) continue;

                    String effectTitle = effect.path("Description").asText().replaceAll("<[^>]*>", "").trim();
                    String finalDetail = effectTitle + ": " + cleanedDescription;

                    switch (name) {
                        case "진화": evolutionDetails.add(finalDetail); break;
                        case "깨달음": realizationDetails.add(finalDetail); break;
                        case "도약": leapDetails.add(finalDetail); break;
                    }
                } catch (JsonProcessingException e) {
                    // Re-throw to ensure transactional integrity
                    throw new RuntimeException("Failed to parse tooltip JSON in populateSpecFromJson", e);
                }
            }

            if (!evolutionDetails.isEmpty()) spec.setArkEvolutionDetail(mapper.writeValueAsString(evolutionDetails));
            if (!realizationDetails.isEmpty()) spec.setArkRealizationDetail(mapper.writeValueAsString(realizationDetails));
            if (!leapDetails.isEmpty()) spec.setArkLeapDetail(mapper.writeValueAsString(leapDetails));
        }
    }

    @Transactional
    public boolean refreshIfHigher(User user, String characterName) {
        requireApiKey(user);

        var opt = characterRepo.findByUserAndName(user, characterName);
        if (opt.isEmpty()) return false;

        var current = opt.get();
        var latestSpecOpt = characterSpecRepo.findFirstByCharacterCharacterIdOrderByUpdatedAtDesc(current.getCharacterId());
        if (latestSpecOpt.isEmpty()) return false;

        var latestSpec = latestSpecOpt.get();
        var p = loa.fetchProfile(user.getUserApiKey(), characterName);
        if (p == null) return false;

        var newItemLevel = parseItemLevel(p.getItemAvgLevel());
        var newCombatPower = parseCombatPowerToLong(p.getCombatPower());

        boolean higher =
                (newItemLevel != null && newItemLevel.compareTo(nvl(latestSpec.getItemLevel())) > 0) ||
                        (newCombatPower != null && nvl(latestSpec.getCombatPower()) != null &&
                                newCombatPower.compareTo(nvl(latestSpec.getCombatPower())) > 0);

        if (!higher) return false;

        current.setServer(p.getServerName());

        CharacterSpec newSpec = new CharacterSpec();
        newSpec.setCharacter(current);
        newSpec.setItemLevel(newItemLevel);
        newSpec.setCombatPower(newCombatPower);

        String arkTitle = loa.fetchArkPassiveTitle(user.getUserApiKey(), p.getCharacterName());
        newSpec.setArkPassive((arkTitle != null && !arkTitle.isBlank()) ? arkTitle : null);

        characterSpecRepo.save(newSpec);

        current.touch();
        characterRepo.save(current);

        return true;
    }

    @Transactional
    public RefreshSummary refreshAllHigher(User user) {
        var myChars = characterRepo.findAllByUser(user);
        int updated = 0, skipped = 0, error = 0;

        for (var ch : myChars) {
            try {
                boolean ok = refreshIfHigher(user, ch.getName());
                if (ok) updated++; else skipped++;
            } catch (Exception e) {
                error++;
                throw new RuntimeException("Failed to refresh character: " + ch.getName(), e);
            }
        }
        return new RefreshSummary(updated, skipped, error);
    }

    private String findDescriptionInTooltip(JsonNode tooltipRoot) {
        if (tooltipRoot == null) return null;
        JsonNode element = tooltipRoot.path("Element_001").path("value").path("Element_000").path("value");
        if (element.isTextual()) return element.asText();
        element = tooltipRoot.path("Element_002").path("value");
        if (element.isTextual()) return element.asText();
        return null;
    }

    private String getJsonNodeAsString(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) return null;
        try {
            return mapper.writeValueAsString(node);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize JsonNode to string", e);
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

    private static BigDecimal parseItemLevel(String raw) {
        if (raw == null || raw.isBlank()) return new BigDecimal("0.00");
        return new BigDecimal(raw.replace(",", ""));
    }

    private static Long parseCombatPowerToLong(String raw) {
        if (raw == null || raw.isBlank()) return null;
        var bd = new BigDecimal(raw.replace(",", ""));
        return bd.setScale(0, BigDecimal.ROUND_HALF_UP).longValueExact();
    }

    private static void requireApiKey(User user) {
        if (user.getUserApiKey() == null || user.getUserApiKey().isBlank()) {
            throw new IllegalStateException("API Key가 필요합니다.");
        }
    }

    private static BigDecimal nvl(BigDecimal v) { return v == null ? new BigDecimal("0.00") : v; }
    private static Long nvl(Long v) { return v == null ? 0L : v; }

    @lombok.Value
    public static class RefreshSummary {
        int updated;
        int skipped;
        int error;
    }

    @Transactional
    public void updateAllCharacterSpecs() {
        List<Character> characters = characterRepo.findAll();
        for (Character character : characters) {
            try {
                updateCharacterSpecIfNeeded(character);
            } catch (Exception e) {
                System.err.println("Failed to update spec for character: " + character.getName());
                throw new RuntimeException("Failed to update spec for character: " + character.getName(), e);
            }
        }
    }

    private void updateCharacterSpecIfNeeded(Character character) throws JsonProcessingException {
        User user = character.getUser();
        requireApiKey(user);

        String armoryJson = loa.fetchArmory(user.getUserApiKey(), character.getName());
        if (armoryJson == null) {
            return;
        }

        JsonNode root = mapper.readTree(armoryJson);
        if (root.isArray()) {
            if (root.size() > 0) root = root.get(0);
            else return;
        }

        JsonNode profile = root.path("ArmoryProfile");
        if (profile.isMissingNode()) {
            return;
        }

        Long newCombatPower = parseCombatPowerToLong(profile.path("CombatPower").asText(null));
        if (newCombatPower == null) {
            return;
        }

        Optional<CharacterSpec> latestSpecOpt = characterSpecRepo.findFirstByCharacterCharacterIdOrderByUpdatedAtDesc(character.getCharacterId());

        boolean shouldUpdate = latestSpecOpt.map(latestSpec -> newCombatPower.compareTo(nvl(latestSpec.getCombatPower())) > 0)
                                          .orElse(true);

        if (shouldUpdate) {
            CharacterSpec newSpec = new CharacterSpec();
            newSpec.setCharacter(character);
            populateSpecFromJson(newSpec, root, character.getName());
            characterSpecRepo.save(newSpec);
        }
    }
}
