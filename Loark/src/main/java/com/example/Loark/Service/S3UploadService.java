package com.example.Loark.Service;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.example.Loark.Entity.Character;
import com.example.Loark.Entity.User;
import com.example.Loark.Repository.CharacterRepository;
import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Service
public class S3UploadService {

    private final AmazonS3 amazonS3;
    private final CharacterRepository characterRepository;
    private final WebClient webClient;

    @Value("${cloud.aws.s3.bucket}")
    private String bucket;

    // WebClient.Builder를 주입받아 FastAPI 서버의 기본 URL을 설정하는 생성자
    public S3UploadService(AmazonS3 amazonS3, CharacterRepository characterRepository, WebClient.Builder webClientBuilder, @Value("${fastapi.server.url}") String fastapiServerUrl) {
        this.amazonS3 = amazonS3;
        this.characterRepository = characterRepository;
        this.webClient = webClientBuilder.baseUrl(fastapiServerUrl).build();
    }

    /**
     * FastAPI 요청 본문을 위한 내부 DTO 클래스
     */
    @Getter
    @Setter
    private static class FastApiOcrRequest {
        private List<String> s3_keys;

        public FastApiOcrRequest(List<String> s3_keys) {
            this.s3_keys = s3_keys;
        }
    }

    /**
     * 파일을 S3에 업로드하고, 파일 키 목록을 FastAPI 서버로 보내 분석을 요청합니다.
     * @param files 업로드할 파일 목록
     * @param characterName 분석을 요청한 캐릭터의 이름
     * @param user 현재 로그인된 사용자 정보
     * @return FastAPI 서버로부터 받은 최종 분석 결과 JSON 문자열
     */
    public String uploadAndAnalyze(List<MultipartFile> files, String characterName, User user) throws IOException {
        // 1. 사용자 정보와 캐릭터 이름으로 DB에서 캐릭터 상세 정보 조회
        Character characterInfo = characterRepository.findByUserAndName(user, characterName)
                .orElseThrow(() -> new IllegalArgumentException("해당 계정에서 캐릭터를 찾을 수 없습니다: " + characterName));

        // 2. S3에 파일 업로드 후, S3 Object Key 목록을 받습니다.
        List<String> s3Keys = uploadFilesToS3(files, characterInfo);

        // 3. FastAPI 서버에 보낼 요청 본문을 구성합니다.
        FastApiOcrRequest requestPayload = new FastApiOcrRequest(s3Keys);

        // 4. WebClient를 사용하여 FastAPI 서버의 분석 엔드포인트를 호출합니다.
        String analysisUrl = "/ocr/process-raid"; // FastAPI 엔드포인트

        // WebClient를 사용하여 비동기 요청을 보내고, 결과를 동기적으로 기다려 String으로 반환합니다.
        return webClient.post()
                .uri(analysisUrl)
                .body(Mono.just(requestPayload), FastApiOcrRequest.class)
                .retrieve() // 요청을 보내고 응답을 받음
                .bodyToMono(String.class) // 응답 본문을 String으로 변환
                .block(); // 동기 방식으로 결과를 기다림
    }

    /**
     * S3에 파일을 업로드하고, 생성된 파일의 Object Key 목록을 반환합니다.
     * @return S3 Object Keys
     */
    private List<String> uploadFilesToS3(List<MultipartFile> files, Character characterInfo) throws IOException {
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

            // URL 대신 파일명(key)을 리스트에 추가
            s3Keys.add(fileName);
        }

        return s3Keys;
    }
}