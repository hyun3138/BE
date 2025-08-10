package com.example.Loark.Service;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class S3UploadService {

    private final AmazonS3 amazonS3;
    private final RestTemplate restTemplate; // RestTemplate 주입

    @Value("${cloud.aws.s3.bucket}")
    private String bucket;

    @Value("${fastapi.server.url}") // FastAPI 서버 URL 주입
    private String fastapiServerUrl;

    /**
     * 파일을 S3에 업로드하고, 파일 키 목록을 FastAPI 서버로 보내 분석을 요청합니다.
     * @param files 업로드할 파일 목록
     * @param mainCharacter 사용자 대표 캐릭터명
     * @return FastAPI 서버로부터 받은 분석 결과
     */
    public ResponseEntity<String> uploadAndAnalyze(List<MultipartFile> files, String mainCharacter) throws IOException {
        // 1. S3에 파일 업로드 후, URL 대신 S3 Object Key 목록을 받습니다.
        List<String> s3Keys = uploadFilesToS3(files, mainCharacter);

        // 2. FastAPI 서버에 보낼 요청 본문을 구성합니다.
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        Map<String, List<String>> requestBody = Collections.singletonMap("s3_keys", s3Keys);
        HttpEntity<Map<String, List<String>>> requestEntity = new HttpEntity<>(requestBody, headers);

        // 3. FastAPI 서버의 분석 엔드포인트를 호출합니다.
        String analysisUrl = fastapiServerUrl + "/analyze-s3-images/";
        
        // FastAPI의 응답을 그대로 반환합니다.
        return restTemplate.postForEntity(analysisUrl, requestEntity, String.class);
    }

    /**
     * S3에 파일을 업로드하고, 생성된 파일의 Object Key 목록을 반환합니다.
     * @return S3 Object Keys
     */
    private List<String> uploadFilesToS3(List<MultipartFile> files, String mainCharacter) throws IOException {
        List<String> s3Keys = new ArrayList<>();
        String uploadTimestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));

        for (int i = 0; i < files.size(); i++) {
            MultipartFile file = files.get(i);

            String originalFilename = file.getOriginalFilename();
            String fileExtension = "";
            if (originalFilename != null && originalFilename.contains(".")) {
                fileExtension = originalFilename.substring(originalFilename.lastIndexOf("."));
            }
            // 파일명 생성 규칙은 그대로 유지
            String fileName = String.format("%s_%s_%d%s", mainCharacter, uploadTimestamp, i + 1, fileExtension);

            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentLength(file.getSize());
            metadata.setContentType(file.getContentType());

            amazonS3.putObject(new PutObjectRequest(bucket, fileName, file.getInputStream(), metadata));

            // URL 대신 파일명(key)을 리스트에 추가
            s3Keys.add(fileName);
        }

        return s3Keys;
    }
}
