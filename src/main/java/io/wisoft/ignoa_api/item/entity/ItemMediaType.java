package io.wisoft.ignoa_api.item.entity;

import io.wisoft.ignoa_api.global.exception.BusinessException;
import io.wisoft.ignoa_api.global.exception.ErrorCode;

import java.util.Set;

public enum ItemMediaType {
    IMAGE, VIDEO;

    private static final Set<String> IMAGE_EXTENSIONS = Set.of("jpg", "jpeg", "png", "gif", "webp");
    private static final Set<String> VIDEO_EXTENSIONS = Set.of("mp4", "mov", "avi", "webm");

    public static ItemMediaType from(String fileName) {
        if (fileName == null) throw new BusinessException(ErrorCode.UNSUPPORTED_MEDIA_TYPE);
        String ext = fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();
        if (IMAGE_EXTENSIONS.contains(ext)) return IMAGE;
        if (VIDEO_EXTENSIONS.contains(ext)) return VIDEO;
        throw new BusinessException(ErrorCode.UNSUPPORTED_MEDIA_TYPE);
    }
}
