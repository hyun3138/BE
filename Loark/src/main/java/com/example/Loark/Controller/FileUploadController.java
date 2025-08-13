package com.example.Loark.Controller;

import com.example.Loark.Entity.User;
import com.example.Loark.Service.S3UploadService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

@RestController
@RequiredArgsConstructor
public class FileUploadController {

    private final S3UploadService s3UploadService;

    @PostMapping("/api/images/upload")
    public ResponseEntity<?> uploadImages(@RequestParam("files") List<MultipartFile> files,
                                          @RequestParam("characterName") String characterName) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof User user)) {
            return new ResponseEntity<>(Map.of("error", "로그인이 필요합니다."), HttpStatus.UNAUTHORIZED);
        }

        if (user.getMainCharacter() == null || user.getMainCharacter().isBlank()) {
            return new ResponseEntity<>(Map.of("error", "로스트아크 계정 인증이 필요합니다."), HttpStatus.BAD_REQUEST);
        }

        if (files.isEmpty()) {
            return new ResponseEntity<>(Map.of("error", "업로드할 파일이 없습니다."), HttpStatus.BAD_REQUEST);
        }

        if (characterName == null || characterName.isBlank()) {
            return new ResponseEntity<>(Map.of("error", "캐릭터를 선택해주세요."), HttpStatus.BAD_REQUEST);
        }

        try {
            // S3 업로드 및 FastAPI 분석을 요청하고, 그 응답(JSON 문자열)을 받습니다.
            String fastApiResultJson = s3UploadService.uploadAndAnalyze(files, characterName, user);

            // FastAPI로부터 받은 JSON 문자열을 성공 응답(200 OK)으로 클라이언트에게 전달합니다.
            // Content-Type을 application/json으로 설정합니다.
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(fastApiResultJson);

        } catch (NoSuchElementException e) {
            // S3UploadService에서 캐릭터를 찾지 못하거나 소유자가 아닐 때 발생하는 예외 처리
            return new ResponseEntity<>(Map.of("error", e.getMessage()), HttpStatus.BAD_REQUEST);
        } catch (HttpClientErrorException e) {
            // FastAPI 서버에서 발생한 오류를 클라이언트에게 전달
            return new ResponseEntity<>(e.getResponseBodyAsString(), e.getStatusCode());
        } catch (IOException e) {
            e.printStackTrace();
            return new ResponseEntity<>(Map.of("error", "파일 처리 중 오류가 발생했습니다."), HttpStatus.INTERNAL_SERVER_ERROR);
        } catch (Exception e) {
            e.printStackTrace();
            return new ResponseEntity<>(Map.of("error", "분석 서버 호출 중 오류가 발생했습니다."), HttpStatus.SERVICE_UNAVAILABLE);
        }
    }
}
