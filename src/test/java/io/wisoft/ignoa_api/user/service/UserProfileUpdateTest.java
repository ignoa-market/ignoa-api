package io.wisoft.ignoa_api.user.service;

import io.wisoft.ignoa_api.global.exception.BusinessException;
import io.wisoft.ignoa_api.global.exception.ErrorCode;
import io.wisoft.ignoa_api.support.IntegrationTestSupport;
import io.wisoft.ignoa_api.user.dto.request.UpdateUserRequest;
import io.wisoft.ignoa_api.user.dto.response.MyProfile;
import io.wisoft.ignoa_api.user.entity.User;
import io.wisoft.ignoa_api.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserProfileUpdateTest extends IntegrationTestSupport {

    @Autowired
    private UserCommandService userCommandService;

    @Autowired
    private UserRepository userRepository;

    @AfterEach
    void tearDown() {
        userRepository.deleteAllInBatch();
    }

    @Test
    void 내_닉네임을_그대로_보내도_중복으로_보지_않는다() {
        User user = userRepository.save(new User("me@test.com", "password", "me", "서울특별시 마포구"));

        MyProfile profile = userCommandService.updateProfile(
                user.getId(), new UpdateUserRequest("me", null, "안녕하세요"));

        assertThat(profile.nickname()).isEqualTo("me");
        assertThat(profile.bio()).isEqualTo("안녕하세요");
    }

    @Test
    void 다른_회원의_닉네임으로_바꾸면_중복_오류가_난다() {
        userRepository.save(new User("other@test.com", "password", "other", "서울특별시 마포구"));
        User user = userRepository.save(new User("me@test.com", "password", "me", "서울특별시 마포구"));

        assertThatThrownBy(() -> userCommandService.updateProfile(
                user.getId(), new UpdateUserRequest("other", null, null)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.DUPLICATE_NAME);
    }
}
