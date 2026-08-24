package io.wisoft.ignoa_api.global.infra.storage;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import org.springframework.beans.factory.annotation.Value;

import io.wisoft.ignoa_api.global.exception.BusinessException;
import io.wisoft.ignoa_api.global.exception.ErrorCode;

import java.io.IOException;
import java.time.LocalDate;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class StorageService {

    private final S3Client s3Client;

    @Value("${cloud.aws.s3.bucket}")
    private String bucket;

    public String upload(MultipartFile file) {
        try {
            String originalFilename = file.getOriginalFilename();

            if (originalFilename == null) {
                throw new BusinessException(ErrorCode.FILE_UPLOAD_FAILED);
            }

            String extension = originalFilename.substring(originalFilename.lastIndexOf("."));
            String objectKey = LocalDate.now() + "/" + UUID.randomUUID() + extension;

            s3Client.putObject(
                    PutObjectRequest.builder()
                            .bucket(bucket)
                            .key(objectKey)
                            .contentType(file.getContentType())
                            .build(),
                    RequestBody.fromBytes(file.getBytes())
            );

            return objectKey;

        } catch (IOException e) {
            throw new BusinessException(ErrorCode.FILE_UPLOAD_FAILED);
        }
    }

    public void delete(String mediaReference) {
        if (mediaReference == null || mediaReference.isBlank()) {
            return;
        }

        if (mediaReference.startsWith("http://") || mediaReference.startsWith("https://")) {
            return;
        }

        s3Client.deleteObject(
                DeleteObjectRequest.builder()
                        .bucket(bucket)
                        .key(mediaReference)
                        .build()
        );
    }
}
