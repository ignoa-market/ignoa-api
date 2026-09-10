package io.wisoft.ignoa_api.auth.oauth;

import io.wisoft.ignoa_api.global.exception.BusinessException;
import io.wisoft.ignoa_api.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Slf4j
@Component
@RequiredArgsConstructor
public class KakaoOAuthClient {

    private final KakaoProperties kakaoProperties;
    private final RestClient restClient;

    public String getAccessToken(String code) {
        KakaoTokenResponse response = restClient.post()
                .uri(kakaoProperties.tokenUri())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body("grant_type=authorization_code" +
                      "&client_id=" + kakaoProperties.clientId() +
                      "&client_secret=" + kakaoProperties.clientSecret() +
                      "&redirect_uri=" + kakaoProperties.redirectUri() +
                      "&code=" + code)
                .retrieve()
                .onStatus(status -> status.isError(), (req, res) -> {
                    if (res.getStatusCode().is5xxServerError()) {
                        log.error("카카오 OAuth 요청 실패: operation=TOKEN_EXCHANGE, status={}", res.getStatusCode());
                    } else {
                        log.warn("카카오 OAuth 요청 거부: operation=TOKEN_EXCHANGE, status={}", res.getStatusCode());
                    }
                    throw new BusinessException(ErrorCode.KAKAO_AUTH_FAILED);
                })
                .body(KakaoTokenResponse.class);

        return response.accessToken();
    }

    public KakaoUserInfoResponse getUserInfo(String accessToken) {
        return restClient.get()
                .uri(kakaoProperties.userInfoUri())
                .header("Authorization", "Bearer " + accessToken)
                .retrieve()
                .onStatus(status -> status.isError(), (req, res) -> {
                    if (res.getStatusCode().is5xxServerError()) {
                        log.error("카카오 OAuth 요청 실패: operation=USER_INFO, status={}", res.getStatusCode());
                    } else {
                        log.warn("카카오 OAuth 요청 거부: operation=USER_INFO, status={}", res.getStatusCode());
                    }
                    throw new BusinessException(ErrorCode.KAKAO_AUTH_FAILED);
                })
                .body(KakaoUserInfoResponse.class);
    }
}
