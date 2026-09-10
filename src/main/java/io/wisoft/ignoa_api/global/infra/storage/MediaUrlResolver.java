package io.wisoft.ignoa_api.global.infra.storage;

import io.wisoft.ignoa_api.user.entity.ProfileImageSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class MediaUrlResolver {

    @Value("${cloud.aws.cloudfront.media-base-url}")
    private String mediaBaseUrl;

    // 외부 URL(카카오)과 S3 Object Key가 함께 저장될 수 있는 프로필 이미지에 사용
    public String toUrl(String reference, ProfileImageSource source) {
        if (reference == null || reference.isBlank()) {
            return null;
        }

        if (ProfileImageSource.EXTERNAL == source) {
            return reference;
        }

        return "%s/%s".formatted(mediaBaseUrl, reference);
    }

    // 항상 S3 Object Key를 저장하는 상품 이미지에 사용
    public String toUrl(String reference) {
        return toUrl(reference, ProfileImageSource.MANAGED);
    }
}
