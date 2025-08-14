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
                if ("play_time".equals(key)) val = val.replaceAll("[()]", "");
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
            String val = cleanSpaces(f.getInferText());

            if ("raid_name".equals(key)) {
                Pattern pattern = Pattern.compile("(.+?)\\[(.+?)\\](.+)");
                Matcher matcher = pattern.matcher(val);
                if (matcher.matches()) {
                    String raidTitle = matcher.group(1).replaceAll("[:.,\\s]", "");
                    String difficulty = matcher.group(2);
                    String gatePart = matcher.group(3);
                    Matcher gateMatcher = Pattern.compile("(\\d+)").matcher(gatePart);
                    String gateNum = gateMatcher.find() ? gateMatcher.group(1) : gatePart;

                    info.put("raid_name", raidTitle);
                    info.put("난이도", difficulty);
                    info.put("관문", gateNum);
                } else {
                    info.put("raid_name", val.replaceAll("[:.,\\s]", ""));
                }
            } else if ("recorded_at".equals(key)) {
                info.put("recorded_at", val.replaceAll("[()]", ""));
            }
        }
    }

    private String cleanSpaces(String text) {
        if (text == null) return "";
        return text.replaceAll("\\s+", "");
    }
}