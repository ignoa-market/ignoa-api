package io.wisoft.ignoa_api.global.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestCookieException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.List;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException e) {
        log.warn("BusinessException: {}", e.getMessage());
        return ResponseEntity
                .status(e.getErrorCode().getHttpStatus())
                .body(ErrorResponse.of(e.getErrorCode()));
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ErrorResponse> handleHandlerMethodValidationException(HandlerMethodValidationException e) {
        log.warn("HandlerMethodValidationException: {}", e.getMessage());
        List<ErrorDetail> details = e.getAllErrors().stream()
                .map(error -> new ErrorDetail(
                        error instanceof FieldError fieldError ? fieldError.getField() : null,
                        error.getDefaultMessage()))
                .toList();
        return ResponseEntity
                .status(ErrorCode.INVALID_INPUT_VALUE.getHttpStatus())
                .body(ErrorResponse.of(ErrorCode.INVALID_INPUT_VALUE, details));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValidException(MethodArgumentNotValidException e) {
        log.warn("MethodArgumentNotValidException: {}", e.getMessage());
        List<ErrorDetail> details = e.getBindingResult().getFieldErrors().stream()
                .map(error -> new ErrorDetail(error.getField(), error.getDefaultMessage()))
                .toList();
        return ResponseEntity
                .status(ErrorCode.INVALID_INPUT_VALUE.getHttpStatus())
                .body(ErrorResponse.of(ErrorCode.INVALID_INPUT_VALUE, details));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentTypeMismatch(MethodArgumentTypeMismatchException e) {
        log.warn("MethodArgumentTypeMismatchException: {}", e.getMessage());
        return ResponseEntity
                .status(ErrorCode.INVALID_PATH_VARIABLE.getHttpStatus())
                .body(ErrorResponse.of(ErrorCode.INVALID_PATH_VARIABLE));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleHttpMessageNotReadable(HttpMessageNotReadableException e) {
        log.warn("HttpMessageNotReadableException: {}", e.getMessage());
        return ResponseEntity
                .status(ErrorCode.INVALID_JSON_FORMAT.getHttpStatus())
                .body(ErrorResponse.of(ErrorCode.INVALID_JSON_FORMAT));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotAllowed(HttpRequestMethodNotSupportedException e) {
        log.warn("HttpRequestMethodNotSupportedException: {}", e.getMessage());
        return ResponseEntity
                .status(ErrorCode.METHOD_NOT_ALLOWED.getHttpStatus())
                .body(ErrorResponse.of(ErrorCode.METHOD_NOT_ALLOWED));
    }

    @ExceptionHandler(MissingRequestCookieException.class)
    public ResponseEntity<ErrorResponse> handleMissingRequestCookie(MissingRequestCookieException e) {
        log.warn("MissingRequestCookieException: {}", e.getMessage());
        return ResponseEntity
                .status(ErrorCode.MISSING_REFRESH_TOKEN.getHttpStatus())
                .body(ErrorResponse.of(ErrorCode.MISSING_REFRESH_TOKEN));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(Exception e) {
        log.error("Exception", e);
        return ResponseEntity
                .status(ErrorCode.INTERNAL_SERVER_ERROR.getHttpStatus())
                .body(ErrorResponse.of(ErrorCode.INTERNAL_SERVER_ERROR));
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleOptimisticLockException(ObjectOptimisticLockingFailureException e) {
        log.warn("OptimisticLockException: {}", e.getMessage());
        return ResponseEntity
                .status(ErrorCode.BID_CONFLICT.getHttpStatus())
                .body(ErrorResponse.of(ErrorCode.BID_CONFLICT));
    }
}
