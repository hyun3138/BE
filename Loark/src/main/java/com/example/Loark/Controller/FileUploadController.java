package com.example.Loark.Controller;

import com.example.Loark.Entity.User;
import com.example.Loark.Service.CharacterService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class FileUploadController {

    private final CharacterService characterService;

    @PostMapping("/api/images/upload")
    public ResponseEntity<?> uploadAndAnalyzeImages(
            @RequestParam("files") List<MultipartFile> files,
            @RequestParam("characterName") String characterName) { // characterName만 받도록 수정

        // 1. 사용자 인증 확인
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof User)) {
            return new ResponseEntity<>(Map.of("error", "로그인이 필요합니다."), HttpStatus.UNAUTHORIZED);
        }

        if (characterName == null || characterName.isBlank()) {
            return new ResponseEntity<>(Map.of("error", "분석할 캐릭터 정보가 없습니다."), HttpStatus.BAD_REQUEST);
        }
        if (files.isEmpty()) {
            return new ResponseEntity<>(Map.of("error", "업로드할 파일이 없습니다."), HttpStatus.BAD_REQUEST);
        }

        List<Map<String, String>> processedFiles = new ArrayList<>();
        List<Map<String, String>> errorFiles = new ArrayList<>();

        for (MultipartFile file : files) {
            try {
                Map<String, String> result = characterService.processFileUpload(file, characterName);
                processedFiles.add(result);
            } catch (Exception e) {
                errorFiles.add(Map.of("filename", file.getOriginalFilename(), "error", e.getMessage()));
            }
        }

        Map<String, Object> finalResponse = new HashMap<>();
        finalResponse.put("message", "총 " + files.size() + "개 파일 처리 완료.");
        finalResponse.put("success_files", processedFiles);
        finalResponse.put("error_files", errorFiles);

        return ResponseEntity.ok(finalResponse);
    }
}
