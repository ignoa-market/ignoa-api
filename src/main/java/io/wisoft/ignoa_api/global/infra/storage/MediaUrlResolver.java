package io.wisoft.ignoa_api.global.infra.storage;

import io.wisoft.ignoa_api.user.entity.ProfileImageSource;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3Client;

@Component
@RequiredArgsConstructor
public class MediaUrlResolver {

    private final S3Client s3Client;

    @Value("${cloud.aws.s3.bucket}")
    private String bucket;

    // 외부 URL(카카오)과 S3 Object Key가 함께 저장될 수 있는 프로필 이미지에 사용
    public String toUrl(String reference, ProfileImageSource source) {
        if (reference == null || reference.isBlank()) {
            return null;
        }

        if (ProfileImageSource.EXTERNAL == source) {
            return reference;
        }

        return s3Client.utilities()
                .getUrl(builder -> builder
                        .bucket(bucket)
                        .key(reference))
                .toExternalForm();
    }

    // 항상 S3 Object Key를 저장하는 상품 이미지에 사용
    public String toUrl(String reference) {
        return toUrl(reference, ProfileImageSource.MANAGED);
    }
}
