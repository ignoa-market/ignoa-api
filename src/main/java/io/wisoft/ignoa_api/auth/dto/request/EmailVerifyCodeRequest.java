package io.wisoft.ignoa_api.auth.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record EmailVerifyCodeRequest(
        @NotBlank(message = "이메일 입력은 필수입니다.")
        @Email(message = "이메일 형식이 올바르지 않습니다.")
        String email) {
}
