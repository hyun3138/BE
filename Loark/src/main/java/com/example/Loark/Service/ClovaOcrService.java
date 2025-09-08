package com.example.Loark.Service;

import com.example.Loark.DTO.clova.ClovaOcrResponse;
import com.example.Loark.DTO.clova.Field;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ClovaOcrService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${clova.ocr.api-url}")
    private String clovaApiUrl;

    @Value("${clova.ocr.secret-key}")
    private String clovaSecretKey;

    public Map<String, Object> analyzeImage(MultipartFile file) throws IOException {
        String encodedImage = Base64.getEncoder().encodeToString(file.getBytes());
        String imageFormat = Objects.requireNonNull(file.getContentType()).substring("image/".length());
        if ("jpeg".equalsIgnoreCase(imageFormat)) imageFormat = "jpg";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-OCR-SECRET", clovaSecretKey);

        Map<String, Object> imagePayload = new HashMap<>();
        imagePayload.put("format", imageFormat);
        imagePayload.put("name", "raid_image");
        imagePayload.put("data", encodedImage);

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("images", Collections.singletonList(imagePayload));
        requestBody.put("lang", "ko");
        requestBody.put("requestId", UUID.randomUUID().toString());
        requestBody.put("version", "V2");
        requestBody.put("timestamp", System.currentTimeMillis());

        HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(requestBody, headers);

        ResponseEntity<ClovaOcrResponse> response = restTemplate.postForEntity(clovaApiUrl, requestEntity, ClovaOcrResponse.class);

        return parseOcrResponse(Objects.requireNonNull(response.getBody()));
    }

    private Map<String, Object> parseOcrResponse(ClovaOcrResponse data) {
        if (data.getImages() == null || data.getImages().isEmpty() || data.getImages().get(0).getMatchedTemplate() == null) {
            throw new RuntimeException("종합정보 이미지만 첨부해주세요");
        }

        String templateName = cleanSpaces(data.getImages().get(0).getMatchedTemplate().getName());
        List<Field> fields = data.getImages().get(0).getFields();

        if ("스킬템플릿".equals(templateName)) {
            throw new IllegalArgumentException("종합정보 이미지만 첨부해주세요. skills");
        }

        if ("종합템플릿".equals(templateName)) {
            return parseGeneralTemplate(fields);
        }

        throw new RuntimeException("알 수 없는 템플릿 유형: " + templateName);
    }

    private Map<String, Object> parseGeneralTemplate(List<Field> fields) {
        Map<String, Object> result = new HashMap<>();
        Map<String, String> info = new HashMap<>();
        extractCommonInfo(fields, info);
        result.put("info", info);

        Map<String, Map<String, String>> mainData = new TreeMap<>();
        Map<String, Map<String, String>> subData = new TreeMap<>();

        for (Field f : fields) {
            String key = cleanSpaces(f.getName());
            String val = cleanSpaces(f.getInferText());

            if (key.startsWith("main_") && key.endsWith("_str")) {
                String g = key.split("_")[1];
                mainData.computeIfAbsent(g, k -> new HashMap<>()).put("str", val);
            } else if (key.startsWith("main_") && key.matches("main_\\d+_1")) {
                String g = key.split("_")[1];
                mainData.computeIfAbsent(g, k -> new HashMap<>()).put("val", val);
            } else if (key.startsWith("sub_") && key.endsWith("_key")) {
                String g = key.split("_")[1];
                subData.computeIfAbsent(g, k -> new HashMap<>()).put("key", val);
            } else if (key.startsWith("sub_") && key.endsWith("_val")) {
                String g = key.split("_")[1];
                subData.computeIfAbsent(g, k -> new HashMap<>()).put("val", val);
            } else if (!key.startsWith("main_") && !key.startsWith("sub_") && !info.containsKey(key)) {
                if ("play_time".equals(key)) {
                    val = val.replaceAll("[()]", "");
                } else if ("clear_date".equals(key)) {
                    try {
                        // 날짜 뒤의 점(.)이 선택적이거나 없는 경우를 모두 처리하는 포맷터
                        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy.MM.dd[. ]HH:mm:ss");
                        // 파싱을 시도하여 형식을 검증합니다. 실패 시 catch 블록으로 이동합니다.
                        LocalDateTime.parse(val, formatter);
                    } catch (DateTimeParseException e) {
                        // 파싱 실패 시, 더 명확한 오류 메시지와 함께 예외를 발생시킵니다.
                        throw new RuntimeException("필드 'clear_date'의 날짜 형식이 잘못되었습니다: '" + val + "'", e);
                    }
                }
                info.put(key, val);
            }
        }

        mainData.forEach((g, dataMap) -> {
            if (dataMap.containsKey("str") && dataMap.containsKey("val") && dataMap.get("val") != null) {
                info.put(dataMap.get("str"), dataMap.get("val"));
            }
        });

        subData.forEach((g, dataMap) -> {
            if (dataMap.containsKey("key") && dataMap.containsKey("val") && dataMap.get("key") != null) {
                info.put(dataMap.get("key"), dataMap.get("val"));
            }
        });

        Map<String, String> cleanedInfo = info.entrySet().stream()
                .filter(entry -> entry.getKey() != null && !entry.getKey().isEmpty())
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue() == null ? "" : e.getValue()));

        result.put("info", cleanedInfo);
        return result;
    }

    private void extractCommonInfo(List<Field> fields, Map<String, String> info) {
        for (Field f : fields) {
            String fieldName = cleanSpaces(f.getName());

            if ("raid_name".equals(fieldName)) {
                String rawVal = f.getInferText();
                if (rawVal == null || rawVal.isEmpty()) {
                    continue;
                }

                String gateNum = "";
                String difficulty = "";
                String raidTitle = "";

                Pattern gatePattern = Pattern.compile("(\\d+)\s*관문\s*$");
                Matcher gateMatcher = gatePattern.matcher(rawVal);
                String remainingPart = rawVal;

                if (gateMatcher.find()) {
                    gateNum = gateMatcher.group(1);
                    remainingPart = rawVal.substring(0, gateMatcher.start()).trim();
                }

                Pattern bracketPattern = Pattern.compile("\\[(.+?)]");
                Matcher bracketMatcher = bracketPattern.matcher(remainingPart);

                if (bracketMatcher.find()) {
                    difficulty = bracketMatcher.group(1).trim();
                    raidTitle = remainingPart.substring(0, bracketMatcher.start()).trim();
                } else {
                    String[] words = remainingPart.trim().split("\s+");
                    LinkedList<String> difficultyWords = new LinkedList<>();
                    int splitPoint = words.length;

                    for (int i = words.length - 1; i >= 0; i--) {
                        String currentWord = words[i];
                        if (Set.of("노말", "하드", "헬").contains(currentWord) || currentWord.matches("^[a-zA-Z]+$")) {
                            difficultyWords.addFirst(currentWord);
                            splitPoint = i;
                        } else {
                            break;
                        }
                    }

                    if (!difficultyWords.isEmpty()) {
                        difficulty = String.join(" ", difficultyWords);
                        List<String> titleWords = new ArrayList<>();
                        for (int i = 0; i < splitPoint; i++) {
                            titleWords.add(words[i]);
                        }
                        raidTitle = String.join(" ", titleWords).trim();
                    } else {
                        raidTitle = remainingPart;
                    }
                }

                info.put("raid_name", raidTitle.replaceAll("[:.,\s]", ""));
                info.put("난이도", difficulty.replaceAll("[:.,\s]", ""));
                info.put("관문", gateNum);

            } else if ("recorded_at".equals(fieldName)) {
                String val = f.getInferText();
                if (val == null) continue;
                String recordedAt = val.replaceAll("[()]", "");
                String digitsOnly = recordedAt.replaceAll("\\D", "");

                String formattedDate = recordedAt;
                if (digitsOnly.length() >= 14) {
                    String year = digitsOnly.substring(0, 4);
                    String month = digitsOnly.substring(4, 6);
                    String day = digitsOnly.substring(6, 8);
                    String hour = digitsOnly.substring(8, 10);
                    String minute = digitsOnly.substring(10, 12);
                    String second = digitsOnly.substring(12, 14);
                    formattedDate = String.format("%s.%s.%s %s:%s:%s", year, month, day, hour, minute, second);
                }
                info.put("recorded_at", formattedDate);
            }
        }
    }

    private String cleanSpaces(String text) {
        if (text == null) return "";
        return text.replaceAll("\s+", "");
    }
}
