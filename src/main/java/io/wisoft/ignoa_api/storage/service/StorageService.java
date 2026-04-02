package io.wisoft.ignoa_api.storage.service;

import io.wisoft.ignoa_api.storage.dto.request.PresignedUrlRequest;
import io.wisoft.ignoa_api.storage.dto.response.PresignedUrlResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import org.springframework.beans.factory.annotation.Value;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;

import io.wisoft.ignoa_api.global.exception.BusinessException;
import io.wisoft.ignoa_api.global.exception.ErrorCode;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDate;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class StorageService {

    private final S3Presigner s3Presigner;
    private final S3Client s3Client;

    @Value("${cloud.aws.s3.bucket}")
    private String bucket;

    @Value("${cloud.aws.s3.endpoint}")
    private String endpoint;

    @Value("${cloud.aws.s3.presigned-url-expiration}")
    private long presignedUrlExpiration;

    public String upload(MultipartFile file) {
        try {
            String originalFilename = file.getOriginalFilename();
            if (originalFilename == null) throw new BusinessException(ErrorCode.FILE_UPLOAD_FAILED);
            String extension = originalFilename.substring(originalFilename.lastIndexOf("."));
            String key = LocalDate.now() + "/" + UUID.randomUUID() + extension;
            s3Client.putObject(
                    PutObjectRequest.builder()
                            .bucket(bucket)
                            .key(key)
                            .contentType(file.getContentType())
                            .build(),
                    RequestBody.fromBytes(file.getBytes())
            );
            return endpoint + "/" + bucket + "/" + key;
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.FILE_UPLOAD_FAILED);
        }
    }

    public void delete(String mediaUrl) {
        String prefix = endpoint + "/" + bucket + "/";
        String key = mediaUrl.substring(prefix.length());
        s3Client.deleteObject(
                DeleteObjectRequest.builder()
                        .bucket(bucket)
                        .key(key)
                        .build()
        );
    }

    public PresignedUrlResponse generatePresignedUrl(PresignedUrlRequest request) {
        String extension = request.fileName().substring(request.fileName().lastIndexOf("."));
        String key = LocalDate.now() + "/" + UUID.randomUUID() + extension;

        PresignedPutObjectRequest presignedRequest = s3Presigner
                .presignPutObject(p -> p.signatureDuration(Duration.ofSeconds(presignedUrlExpiration))
                        .putObjectRequest(por -> por
                                .bucket(bucket)
                                .key(key)
                                .contentType(request.contentType())
                                .build())
                        .build()
                );

        String presignedUrl = presignedRequest.url().toString();
        String objectUrl = endpoint + "/" + bucket + "/" + key;

        return new PresignedUrlResponse(presignedUrl, objectUrl);
    }
}
