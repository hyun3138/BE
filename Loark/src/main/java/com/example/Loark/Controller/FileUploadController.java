package com.example.Loark.Controller;

import com.example.Loark.Entity.User;
import com.example.Loark.Service.ClovaOcrService;
import com.example.Loark.Service.S3UploadService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class FileUploadController {

    private final ClovaOcrService clovaOcrService;
    private final S3UploadService s3UploadService;

    @PostMapping("/api/images/upload")
    public ResponseEntity<?> uploadAndAnalyzeImages(
            @RequestParam("files") List<MultipartFile> files,
            @RequestParam("characterName") String characterInfo) { // 상세 정보가 담긴 캐릭터 문자열 받기

        // 1. 사용자 인증 확인
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof User)) {
            return new ResponseEntity<>(Map.of("error", "로그인이 필요합니다."), HttpStatus.UNAUTHORIZED);
        }

        if (characterInfo == null || characterInfo.isBlank()) {
            return new ResponseEntity<>(Map.of("error", "분석할 캐릭터 정보가 없습니다."), HttpStatus.BAD_REQUEST);
        }
        if (files.isEmpty()) {
            return new ResponseEntity<>(Map.of("error", "업로드할 파일이 없습니다."), HttpStatus.BAD_REQUEST);
        }

        //TODO: 아래 TODO 해결핼때 지우기
        String sanitizedCharacterInfo = characterInfo
                .replaceAll("\\s*/\\s*", "_") // " / " 를 "-" 로 변경
                .replaceAll("[\\s()]", "");      // 나머지 공백과 괄호 제거


        List<Map<String, String>> processedFiles = new ArrayList<>();
        List<Map<String, String>> errorFiles = new ArrayList<>();
        String uploadTimestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));

        for (int i = 0; i < files.size(); i++) {
            MultipartFile file = files.get(i);
            String originalFilename = file.getOriginalFilename();

            try {
                // 2. Clova OCR로 이미지 분석
                Map<String, Object> parsedData = clovaOcrService.analyzeImage(file);
                @SuppressWarnings("unchecked")
                Map<String, String> info = (Map<String, String>) parsedData.get("info");


                //TODO:
                String recorded_at = info.getOrDefault("recorded_at", "unknown");
                /**
                 * 파일에 저장하기 전에 여기에 캐릭터명으로 로스아크API 호출
                 * -> DB에 저장되어있는 캐릭터 테이블에 있는 전투력과 인식되어 나온 recorded_at 이전 가까운 장비 정보 저장되어있는 DB에 전투력과비교해서
                 * -> 높은쪽의 전투력의 정보를 json으로 같이저장
                 * MAX_ARMOR(PFK: 캐릭터명) 테이블 & UPDATE_ARMOR(FK: 캐릭터명) 테이블 생성 필요
                 * info:{}, armor:{}
                 * **/

                // 3. 파일명 생성 (가공된 캐릭터 정보 사용)
                String imageS3Key = String.format("%s_%s_%d.png", sanitizedCharacterInfo, uploadTimestamp, i + 1);

                String raidName = info.getOrDefault("raid_name", "unknown");
                String difficulty = info.getOrDefault("난이도", "unknown");
                String gate = info.getOrDefault("관문", "unknown");
                String jsonS3Key = String.format("%s_%s_%s_%s_%s.json",
                        sanitizedCharacterInfo, raidName, difficulty, gate, uploadTimestamp);

                // 4. S3에 이미지와 JSON 업로드
                String imageUrl = s3UploadService.uploadImage(file, imageS3Key);
                String jsonUrl = s3UploadService.uploadJson(parsedData, jsonS3Key);

                Map<String, String> successResult = new HashMap<>();
                successResult.put("original_filename", originalFilename);
                successResult.put("image_url", imageUrl);
                successResult.put("json_url", jsonUrl);
                processedFiles.add(successResult);

            } catch (IllegalArgumentException e) {
                // '스킬템플릿' 등 비즈니스 로직 오류 처리
                return new ResponseEntity<>(Map.of("error", e.getMessage(), "filename", originalFilename), HttpStatus.BAD_REQUEST);
            } catch (IOException e) {
                errorFiles.add(Map.of("filename", originalFilename, "error", "파일 처리 중 오류 발생: " + e.getMessage()));
            } catch (Exception e) {
                errorFiles.add(Map.of("filename", originalFilename, "error", "분석 또는 업로드 중 오류 발생: " + e.getMessage()));
            }
        }

        Map<String, Object> finalResponse = new HashMap<>();
        finalResponse.put("message", "총 " + files.size() + "개 파일 처리 완료.");
        finalResponse.put("success_files", processedFiles);
        finalResponse.put("error_files", errorFiles);

        return ResponseEntity.ok(finalResponse);
    }
}