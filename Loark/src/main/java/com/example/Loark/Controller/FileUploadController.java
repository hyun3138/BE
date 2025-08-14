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

        List<String> uploadedS3Keys = null;

        try {
            // 1단계: S3에 파일을 업로드하고 키 목록을 받습니다.
            uploadedS3Keys = s3UploadService.uploadFilesToS3(files, characterName, user);
            System.out.println("Successfully uploaded S3 Keys: " + uploadedS3Keys);

            // 2단계: 받은 키 목록으로 FastAPI 분석을 요청합니다.
            String fastApiResultJson = s3UploadService.analyzeWithFastApi(uploadedS3Keys);

            // 3단계: 모든 과정이 성공하면, 결과를 클라이언트에게 반환합니다.
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(fastApiResultJson);

        } catch (NoSuchElementException e) {
            // 이 예외는 uploadFilesToS3에서 발생할 수 있으며, 이 시점에는 uploadedS3Keys가 null입니다.
            // 따라서 여기서는 삭제 로직이 필요 없습니다.
            return new ResponseEntity<>(Map.of("error", e.getMessage()), HttpStatus.BAD_REQUEST);
        } catch (HttpClientErrorException e) {
            // 이 예외는 analyzeWithFastApi에서 발생합니다. 이 시점에는 uploadedS3Keys에 값이 있습니다.
            s3UploadService.deleteS3Objects(uploadedS3Keys);
            return new ResponseEntity<>(e.getResponseBodyAsString(), e.getStatusCode());
        } catch (IOException e) {
            // 이 예외는 uploadFilesToS3에서 발생할 수 있습니다.
            e.printStackTrace();
            // IOException 발생 시점에는 일부 파일만 업로드되었을 수 있으므로, 삭제를 시도합니다.
            s3UploadService.deleteS3Objects(uploadedS3Keys);
            return new ResponseEntity<>(Map.of("error", "파일 처리 중 오류가 발생했습니다. 관리자에게 문의해주세요"), HttpStatus.INTERNAL_SERVER_ERROR);
        } catch (Exception e) {
            // 그 외 모든 예외 처리
            e.printStackTrace();
            s3UploadService.deleteS3Objects(uploadedS3Keys);
            return new ResponseEntity<>(Map.of("error", "종합정보만 첨부 해주세요"), HttpStatus.SERVICE_UNAVAILABLE);
        }
    }
}
