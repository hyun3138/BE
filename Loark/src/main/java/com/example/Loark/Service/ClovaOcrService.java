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

    /**
     * 이미지 파일을 받아 Clova OCR API를 호출하고, 결과를 파싱하여 Map으로 반환합니다.
     * @param file 분석할 이미지 파일
     * @return 파싱된 데이터 Map
     * @throws IOException 파일 처리 오류
     * @throws IllegalArgumentException '스킬템플릿'이 감지되었을 경우
     */
    public Map<String, Object> analyzeImage(MultipartFile file) throws IOException {
        String encodedImage = Base64.getEncoder().encodeToString(file.getBytes());
        String imageFormat = Objects.requireNonNull(file.getContentType()).substring("image/".length());
        if ("jpeg".equalsIgnoreCase(imageFormat)) imageFormat = "jpg";

        // Clova API 요청 본문 생성
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

        // API 호출
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
            String val = f.getInferText(); // 공백 제거는 각 필드 처리 시 필요에 따라 수행

            if (key.startsWith("main_") && key.endsWith("_str")) {
                String g = key.split("_")[1];
                mainData.computeIfAbsent(g, k -> new HashMap<>()).put("str", cleanSpaces(val));
            } else if (key.startsWith("main_") && key.matches("main_\\d+_1")) {
                String g = key.split("_")[1];
                mainData.computeIfAbsent(g, k -> new HashMap<>()).put("val", cleanSpaces(val));
            } else if (key.startsWith("sub_") && key.endsWith("_key")) {
                String g = key.split("_")[1];
                subData.computeIfAbsent(g, k -> new HashMap<>()).put("key", cleanSpaces(val));
            } else if (key.startsWith("sub_") && key.endsWith("_val")) {
                String g = key.split("_")[1];
                subData.computeIfAbsent(g, k -> new HashMap<>()).put("val", cleanSpaces(val));
            } else if (!key.startsWith("main_") && !key.startsWith("sub_") && !info.containsKey(key)) {
                if ("play_time".equals(key) && val != null) {
                    val = val.replaceAll("[()]", "");
                } else if ("clear_date".equals(key) && val != null) {
                    try {
                        // 점이 있거나 없는 날짜 형식을 모두 해석하는 유연한 파서
                        DateTimeFormatter flexibleParser = DateTimeFormatter.ofPattern("yyyy.MM.dd[. ]HH:mm:ss");
                        LocalDateTime parsedDateTime = LocalDateTime.parse(val, flexibleParser);

                        // 앱 전체에서 사용할 표준 포맷터 (항상 점을 포함)
                        DateTimeFormatter canonicalFormatter = DateTimeFormatter.ofPattern("yyyy.MM.dd. HH:mm:ss");
                        val = parsedDateTime.format(canonicalFormatter);

                    } catch (DateTimeParseException e) {
                        // 파싱 실패 시에도 원본 값을 유지하거나, 오류 로깅을 할 수 있음
                        // 여기서는 명확한 오류를 위해 예외를 던짐
                        throw new RuntimeException("필드 'clear_date'의 날짜 형식이 잘못되었습니다: '" + val + "'", e);
                    }
                }
                info.put(key, cleanSpaces(val));
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

        // Filter out entries with null or empty keys
        Map<String, String> cleanedInfo = info.entrySet().stream()
                .filter(entry -> entry.getKey() != null && !entry.getKey().isEmpty())
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue() == null ? "" : e.getValue()));

        result.put("info", cleanedInfo);
        return result;
    }

    private void extractCommonInfo(List<Field> fields, Map<String, String> info) {
        for (Field f : fields) {
            String key = cleanSpaces(f.getName());

            if ("raid_name".equals(key)) {
                String rawVal = f.getInferText();
                if (rawVal == null || rawVal.trim().isEmpty()) {
                    continue;
                }

                String processedVal = rawVal.trim();

                // 전처리 단계: 대괄호가 없고, "영어단어" + "관문" 패턴이 있으면 영어를 대괄호로 감싼다.
                if (!processedVal.contains("[") && !processedVal.contains("]")) {
                    Pattern englishPattern = Pattern.compile("^(.+?)\\s+([a-zA-Z\\s]+?)\\s+(\\d+\\s*관문\\s*)$");
                    Matcher englishMatcher = englishPattern.matcher(processedVal);
                    if (englishMatcher.matches()) {
                        String raidPart = englishMatcher.group(1);
                        String englishDifficulty = englishMatcher.group(2).trim();
                        String gatePart = englishMatcher.group(3);
                        processedVal = String.format("%s [%s] %s", raidPart, englishDifficulty, gatePart);
                    }
                }

                // 기존 로직: 대괄호가 있는 경우를 파싱
                Pattern pattern = Pattern.compile("(.+?)\\[(.+?)\\](.+)");
                Matcher matcher = pattern.matcher(processedVal);
                if (matcher.matches()) {
                    String raidTitle = matcher.group(1).replaceAll("[:.,\\s]", "");
                    String difficulty = matcher.group(2).replaceAll("\\s+", "");
                    String gatePart = matcher.group(3);
                    Matcher gateMatcher = Pattern.compile("(\\d+)").matcher(gatePart);
                    String gateNum = gateMatcher.find() ? gateMatcher.group(1) : "";

                    info.put("raid_name", raidTitle);
                    info.put("난이도", difficulty);
                    info.put("관문", gateNum);
                } else {
                    // 폴백 로직: 대괄호가 없는 경우 (예: 노말, 하드 난이도 또는 난이도 없음)
                    String raidTitle = processedVal;
                    String gateNum = "";
                    String difficulty = "";

                    Pattern gatePattern = Pattern.compile("(\\d+)\\s*관문\\s*$");
                    Matcher gateMatcher = gatePattern.matcher(raidTitle);
                    if (gateMatcher.find()) {
                        gateNum = gateMatcher.group(1);
                        raidTitle = raidTitle.substring(0, gateMatcher.start()).trim();
                    }
                    
                    String[] words = raidTitle.split("\\s+");
                    if (words.length > 1) {
                        String lastWord = words[words.length - 1];
                        if (Set.of("노말", "하드", "헬").contains(lastWord)) {
                            difficulty = lastWord;
                            raidTitle = raidTitle.substring(0, raidTitle.lastIndexOf(lastWord)).trim();
                        }
                    }

                    info.put("raid_name", raidTitle.replaceAll("[:.,\\s]", ""));
                    info.put("난이도", difficulty);
                    info.put("관문", gateNum);
                }
            } else if ("recorded_at".equals(key)) {
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
                    // 버그 수정: 날짜와 시간 사이에 공백 추가
                    formattedDate = String.format("%s.%s.%s%s:%s:%s", year, month, day, hour, minute, second);
                }
                info.put("recorded_at", formattedDate);
            }
        }
    }

    private String cleanSpaces(String text) {
        if (text == null) return "";
        return text.replaceAll("\\s+", "");
    }
}
