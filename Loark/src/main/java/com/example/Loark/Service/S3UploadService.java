package com.example.Loark.Service;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class S3UploadService {

    private final AmazonS3 amazonS3;
    private final ObjectMapper objectMapper;

    @Value("${s3.bucket.images}")
    private String imageBucket;

    @Value("${s3.bucket.json}")
    private String jsonBucket;

    /**
     * MultipartFile을 S3 이미지 버킷에 업로드합니다.
     * @param file 업로드할 이미지 파일
     * @param s3Key 저장할 S3 객체 키 (파일명)
     * @return 업로드된 파일의 S3 URL
     */
    public String uploadImage(MultipartFile file, String s3Key) throws IOException {
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength(file.getSize());
        metadata.setContentType(file.getContentType());

        amazonS3.putObject(new PutObjectRequest(imageBucket, s3Key, file.getInputStream(), metadata));
        return amazonS3.getUrl(imageBucket, s3Key).toString();
    }

    /**
     * 파싱된 JSON 데이터(Map)를 S3 JSON 버킷에 예쁘게 포맷팅하여 업로드합니다.
     * @param jsonData 저장할 JSON 데이터
     * @param s3Key 저장할 S3 객체 키 (파일명)
     * @return 업로드된 파일의 S3 URL
     */
    public String uploadJson(Map<String, Object> jsonData, String s3Key) throws JsonProcessingException {
        // ★★★ 변경된 부분: writerWithDefaultPrettyPrinter()를 사용하여 JSON을 예쁘게 포맷팅합니다. ★★★
        byte[] jsonBytes = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(jsonData);
        InputStream jsonInputStream = new ByteArrayInputStream(jsonBytes);

        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength(jsonBytes.length);
        metadata.setContentType("application/json");
        metadata.setContentEncoding("UTF-8");

        amazonS3.putObject(new PutObjectRequest(jsonBucket, s3Key, jsonInputStream, metadata));
        return amazonS3.getUrl(jsonBucket, s3Key).toString();
    }
}