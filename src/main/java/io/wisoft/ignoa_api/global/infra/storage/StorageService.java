package io.wisoft.ignoa_api.global.infra.storage;

import lombok.RequiredArgsConstructor;
import org.apache.tika.Tika;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import io.wisoft.ignoa_api.global.exception.BusinessException;
import io.wisoft.ignoa_api.global.exception.ErrorCode;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class StorageService {

    private static final Map<String, String> ALLOWED_MEDIA_TYPES = Map.of(
            "image/jpeg", "jpg",
            "image/png", "png",
            "image/gif", "gif",
            "image/webp", "webp",
            "video/mp4", "mp4",
            "video/quicktime", "mov",
            "video/x-msvideo", "avi",
            "video/webm", "webm"
    );

    private final S3Client s3Client;
    private final Tika tika = new Tika();

    @Value("${cloud.aws.s3.bucket}")
    private String bucket;

    public StorageUploadResult upload(MultipartFile file, ObjectKeyPrefix prefix) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.EMPTY_FILE);
        }

        String contentType = detectContentType(file);
        String extension = resolveExtension(contentType);
        validateAllowedType(prefix, contentType);

        String objectKey = "%s/%s.%s".formatted(
                prefix.getValue(),
                UUID.randomUUID(),
                extension
        );

        try (InputStream inputStream = file.getInputStream()) {
            s3Client.putObject(
                    PutObjectRequest.builder()
                            .bucket(bucket)
                            .key(objectKey)
                            .contentType(contentType)
                            .build(),
                    RequestBody.fromInputStream(inputStream, file.getSize())
            );

            return new StorageUploadResult(objectKey, contentType);
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.FILE_UPLOAD_FAILED);
        }
    }

    public void delete(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            return;
        }

        s3Client.deleteObject(
                DeleteObjectRequest.builder()
                        .bucket(bucket)
                        .key(objectKey)
                        .build()
        );
    }

    private String detectContentType(MultipartFile file) {
        try (InputStream inputStream = file.getInputStream()) {
            return tika.detect(inputStream);
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.FILE_UPLOAD_FAILED);
        }
    }

    private String resolveExtension(String contentType) {
        String extension = ALLOWED_MEDIA_TYPES.get(contentType);

        if (extension == null) {
            throw new BusinessException(ErrorCode.UNSUPPORTED_MEDIA_TYPE);
        }

        return extension;
    }

    private void validateAllowedType(ObjectKeyPrefix prefix, String contentType) {
        if (prefix == ObjectKeyPrefix.PROFILES && !contentType.startsWith("image/")) {
            throw new BusinessException(ErrorCode.UNSUPPORTED_MEDIA_TYPE);
        }
    }
}
