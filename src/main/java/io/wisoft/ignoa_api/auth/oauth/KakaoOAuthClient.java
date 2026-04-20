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
                    log.error("카카오 토큰 교환 실패 - status: {}, body: {}", res.getStatusCode(), new String(res.getBody().readAllBytes()));
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
                    log.error("카카오 사용자 정보 조회 실패 - status: {}, body: {}", res.getStatusCode(), new String(res.getBody().readAllBytes()));
                    throw new BusinessException(ErrorCode.KAKAO_AUTH_FAILED);
                })
                .body(KakaoUserInfoResponse.class);
    }
}
