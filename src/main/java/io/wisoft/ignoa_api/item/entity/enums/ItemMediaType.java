package io.wisoft.ignoa_api.item.entity.enums;

import io.wisoft.ignoa_api.global.exception.BusinessException;
import io.wisoft.ignoa_api.global.exception.ErrorCode;

import java.util.Set;

public enum ItemMediaType {
    IMAGE, VIDEO;

    private static final Set<String> IMAGE_TYPES = Set.of("image/jpeg", "image/png", "image/gif", "image/webp");
    private static final Set<String> VIDEO_TYPES = Set.of("video/mp4", "video/quicktime", "video/avi", "video/webm");

    public static ItemMediaType from(String contentType) {
        if (contentType == null) throw new BusinessException(ErrorCode.UNSUPPORTED_MEDIA_TYPE);
        if (IMAGE_TYPES.contains(contentType)) return IMAGE;
        if (VIDEO_TYPES.contains(contentType)) return VIDEO;
        throw new BusinessException(ErrorCode.UNSUPPORTED_MEDIA_TYPE);
    }
}
