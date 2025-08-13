package com.example.Loark.Service;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.example.Loark.Entity.Character;
import com.example.Loark.Entity.User;
import com.example.Loark.Repository.CharacterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
public class S3UploadService {

    private final AmazonS3 amazonS3;
    private final CharacterRepository characterRepository;
    private final WebClient webClient;

    @Value("${cloud.aws.s3.bucket}")
    private String bucket;

    /**
     * 파일을 S3에 업로드하고, 생성된 파일의 Object Key 목록을 반환합니다.
     */
    public List<String> uploadFilesToS3(List<MultipartFile> files, String characterName, User user) throws IOException {
        System.out.println("Attempting to find character for User ID: " + user.getUserId() + ", Character Name: " + characterName);
        Character characterInfo = characterRepository.findByUserAndName(user, characterName)
                .orElseThrow(() -> new NoSuchElementException("해당 계정에서 캐릭터를 찾을 수 없습니다: " + characterName));

        List<String> s3Keys = new ArrayList<>();
        String uploadTimestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));

        for (int i = 0; i < files.size(); i++) {
            MultipartFile file = files.get(i);

            String originalFilename = file.getOriginalFilename();
            String fileExtension = "";
            if (originalFilename != null && originalFilename.contains(".")) {
                fileExtension = originalFilename.substring(originalFilename.lastIndexOf("."));
            }

            // 파일명 생성 규칙: 캐릭터이름_직업_아이템레벨_전투력_타임스탬프_인덱스.확장자
            String fileName = String.format("%s_%s_%s_%s_%s_%s_%d%s",
                    characterInfo.getName(),
                    characterInfo.getClazz(),
                    characterInfo.getArkPassive(),
                    characterInfo.getItemLevel(),
                    characterInfo.getCombatPower(),
                    uploadTimestamp,
                    i + 1,
                    fileExtension);

            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentLength(file.getSize());
            metadata.setContentType(file.getContentType());

            amazonS3.putObject(new PutObjectRequest(bucket, fileName, file.getInputStream(), metadata));
            s3Keys.add(fileName);
        }
        return s3Keys;
    }

    /**
     * S3 키 목록을 사용하여 FastAPI 서버에 분석을 요청합니다.
     */
    public String analyzeWithFastApi(List<String> s3Keys) {
        Map<String, List<String>> requestPayload = new HashMap<>();
        requestPayload.put("s3_keys", s3Keys);

        String analysisUrl = "/ocr/process-raid";

        return webClient.post()
                .uri(analysisUrl)
                .bodyValue(requestPayload)
                .retrieve()
                .bodyToMono(String.class)
                .block(); // 동기 방식으로 결과를 기다림
    }

    /**
     * S3에서 지정된 객체들을 삭제합니다.
     */
    public void deleteS3Objects(List<String> s3Keys) {
        if (s3Keys != null && !s3Keys.isEmpty()) {
            System.out.println("Attempting to delete S3 objects: " + s3Keys); // 삭제 시도 로그 추가
            for (String key : s3Keys) {
                try {
                    amazonS3.deleteObject(bucket, key);
                    System.out.println("Deleted S3 object: " + key);
                } catch (Exception e) {
                    System.err.println("Failed to delete S3 object " + key + ": " + e.getMessage());
                }
            }
        }
    }
}
