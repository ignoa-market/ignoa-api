package io.wisoft.ignoa_api.auth.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record SignupRequest(
        @NotBlank(message = "이메일 입력은 필수입니다.")
        @Email
        String email,

        @NotBlank(message = "비밀번호 입력은 필수입니다.")
        String password,

        @NotBlank(message = "닉네임 입력은 필수입니다.")
        String nickname,

        @NotBlank(message = "주소 입력은 필수입니다.")
        String address
) {
}
