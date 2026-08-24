package io.wisoft.ignoa_api.global.infra.storage;

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

    public String toUrl(String mediaReference) {
        if (mediaReference == null || mediaReference.isBlank()) {
            return null;
        }

        if (mediaReference.startsWith("http://") || mediaReference.startsWith("https://")) {
            return mediaReference;
        }

        return s3Client.utilities()
                .getUrl(builder -> builder
                        .bucket(bucket)
                        .key(mediaReference))
                .toExternalForm();
    }
}
