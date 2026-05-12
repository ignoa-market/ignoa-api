package io.wisoft.ignoa_api.user.controller;

import io.wisoft.ignoa_api.global.common.ApiResponse;
import io.wisoft.ignoa_api.user.dto.request.UpdateUserRequest;
import io.wisoft.ignoa_api.user.dto.response.UserMeResponse;
import io.wisoft.ignoa_api.user.service.UserCommandService;
import io.wisoft.ignoa_api.user.service.UserFacade;
import io.wisoft.ignoa_api.user.service.UserQueryService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import static io.wisoft.ignoa_api.global.common.CookieUtils.createClearRefreshTokenCookie;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserCommandService userCommandService;
    private final UserQueryService userQueryService;
    private final UserFacade userFacade;

    @GetMapping("/email/duplicate")
    public ResponseEntity<ApiResponse<Void>> checkDuplicateEmail(@RequestParam String email) {
        userQueryService.checkDuplicateEmail(email);
        ApiResponse<Void> response = ApiResponse.of(null, "사용 가능한 이메일입니다.");
        return ResponseEntity.ok(response);
    }

    @GetMapping("/nickname/duplicate")
    public ResponseEntity<ApiResponse<Void>> checkDuplicateNickname(@RequestParam String nickname) {
        userQueryService.checkDuplicateNickname(nickname);
        ApiResponse<Void> response = ApiResponse.of(null, "사용 가능한 닉네임입니다.");
        return ResponseEntity.ok(response);
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserMeResponse>> getMe(@AuthenticationPrincipal Long userId) {
        UserMeResponse data = userQueryService.getMe(userId);
        ApiResponse<UserMeResponse> response = ApiResponse.of(data, "마이페이지 조회에 성공했습니다.");
        return ResponseEntity.ok(response);
    }

    @PatchMapping(value = "/me/profile-image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<UserMeResponse>> updateProfileImage(
            @AuthenticationPrincipal Long userId,
            @RequestPart MultipartFile image
    ) {
        UserMeResponse data = userFacade.updateProfileImage(userId, image);
        ApiResponse<UserMeResponse> response = ApiResponse.of(data, "프로필 사진이 변경되었습니다.");
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/me/profile-image")
    public ResponseEntity<ApiResponse<Void>> deleteProfileImage(@AuthenticationPrincipal Long userId) {
        userCommandService.deleteProfileImage(userId);
        ApiResponse<Void> response = ApiResponse.of(null, "프로필 사진이 삭제되었습니다.");
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/me")
    public ResponseEntity<ApiResponse<UserMeResponse>> updateProfile(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody UpdateUserRequest request
    ) {
        UserMeResponse data = userCommandService.updateProfile(userId, request);
        ApiResponse<UserMeResponse> response = ApiResponse.of(data, "유저 정보가 수정되었습니다.");
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/me")
    public ResponseEntity<ApiResponse<Void>> deleteMe(
            @AuthenticationPrincipal Long userId,
            @RequestHeader("Authorization") String authHeader,
            @CookieValue("refresh_token") String refreshToken,
            HttpServletResponse response
    ) {
        userFacade.deleteMe(userId, authHeader.substring(7), refreshToken);

        ResponseCookie clearCookie = createClearRefreshTokenCookie();
        response.addHeader(HttpHeaders.SET_COOKIE, clearCookie.toString());

        return ResponseEntity.ok(ApiResponse.of(null, "회원 탈퇴가 되었습니다."));
    }
}
