package io.wisoft.ignoa_api.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // Common
    INVALID_INPUT_VALUE(HttpStatus.BAD_REQUEST, "입력값이 올바르지 않습니다."),
    INVALID_JSON_FORMAT(HttpStatus.BAD_REQUEST, "JSON 형식이 올바르지 않습니다."),
    INVALID_PATH_VARIABLE(HttpStatus.BAD_REQUEST, "경로 변수 타입이 올바르지 않습니다."),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "지원하지 않는 HTTP 메서드입니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다."),

    // Auth
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다."),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "유효하지 않은 토큰입니다."),
    MISSING_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "Refresh Token이 존재하지 않습니다."),

    // User
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."),
    DUPLICATE_EMAIL(HttpStatus.CONFLICT, "이미 존재하는 이메일입니다."),
    DUPLICATE_NAME(HttpStatus.CONFLICT, "이미 사용 중인 닉네임입니다."),
    HAS_ACTIVE_AUCTION(HttpStatus.CONFLICT, "진행 중인 경매가 있어 탈퇴할 수 없습니다."),
    HAS_ACTIVE_BID(HttpStatus.CONFLICT, "진행 중인 경매에 입찰 중이어서 탈퇴할 수 없습니다."),
    ACCOUNT_PENDING_DELETION(HttpStatus.FORBIDDEN, "탈퇴 처리 중인 계정입니다."),
    ACCOUNT_NOT_RECOVERABLE(HttpStatus.BAD_REQUEST, "복구 가능한 계정이 아닙니다."),

    // OAuth
    KAKAO_AUTH_FAILED(HttpStatus.UNAUTHORIZED, "카카오 인증에 실패했습니다."),
    KAKAO_EMAIL_REQUIRED(HttpStatus.BAD_REQUEST, "카카오 계정의 이메일 제공 동의가 필요합니다."),

    // Email
    EMAIL_SEND_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "이메일 발송에 실패했습니다."),
    INVALID_VERIFICATION_CODE(HttpStatus.BAD_REQUEST, "인증 코드가 올바르지 않습니다."),
    EMAIL_NOT_VERIFIED(HttpStatus.BAD_REQUEST, "이메일 인증이 완료되지 않았습니다."),

    // Item
    ITEM_NOT_FOUND(HttpStatus.NOT_FOUND, "상품을 찾을 수 없습니다."),
    ITEM_UPDATE_FORBIDDEN(HttpStatus.FORBIDDEN, "본인 상품만 수정할 수 있습니다."),
    ITEM_DELETE_FORBIDDEN(HttpStatus.FORBIDDEN, "본인 상품만 삭제할 수 있습니다."),
    ITEM_MEDIA_NOT_FOUND(HttpStatus.NOT_FOUND, "상품 미디어를 찾을 수 없습니다."),
    ITEM_MEDIA_REQUIRED(HttpStatus.BAD_REQUEST, "상품 미디어는 최소 1개 이상이어야 합니다."),
    AUCTION_ALREADY_CLOSED(HttpStatus.BAD_REQUEST, "이미 마감된 경매입니다."),
    INVALID_BUY_NOW_PRICE(HttpStatus.BAD_REQUEST, "즉시 구매가는 현재 입찰가보다 낮을 수 없습니다."),
    COMPLETED_ITEM_CANNOT_BE_DELETED(HttpStatus.CONFLICT, "거래가 완료된 상품은 삭제할 수 없습니다."),

    // Bid
    INVALID_BID_PRICE(HttpStatus.BAD_REQUEST, "입찰 금액은 현재 최고가보다 높아야 합니다."),
    AUCTION_CLOSED(HttpStatus.BAD_REQUEST, "종료된 경매에는 입찰할 수 없습니다."),
    SELF_BID_NOT_ALLOWED(HttpStatus.FORBIDDEN, "본인 상품에는 입찰할 수 없습니다."),
    SELF_BUY_NOT_ALLOWED(HttpStatus.FORBIDDEN, "본인 상품은 구매할 수 없습니다."),

    // Storage
    FILE_UPLOAD_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "파일 업로드에 실패했습니다."),
    PROFILE_IMAGE_NOT_FOUND(HttpStatus.NOT_FOUND, "프로필 이미지를 찾을 수 없습니다."),
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.BAD_REQUEST, "지원하지 않는 파일 형식입니다."),

    // Wish
    WISH_NOT_FOUND(HttpStatus.NOT_FOUND, "찜을 찾을 수 없습니다."),
    WISH_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 찜한 상품입니다.");

    private final HttpStatus httpStatus;
    private final String message;
}

