package io.wisoft.ignoa_api.user.entity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserTest {

    @Test
    void 자기소개를_입력하면_저장된다() {
        User user = new User("user@test.com", "password", "user", "서울특별시 마포구");

        user.updateProfile(null, null, "안녕하세요");

        assertThat(user.getBio()).isEqualTo("안녕하세요");
    }

    @Test
    void 자기소개의_앞뒤_공백은_지우고_저장한다() {
        User user = new User("user@test.com", "password", "user", "서울특별시 마포구");

        user.updateProfile(null, null, "  안녕하세요\n\n");

        assertThat(user.getBio()).isEqualTo("안녕하세요");
    }

    @Test
    void 자기소개가_null이면_기존_값을_유지한다() {
        User user = new User("user@test.com", "password", "user", "서울특별시 마포구");
        user.updateProfile(null, null, "안녕하세요");

        user.updateProfile("newName", null, null);

        assertThat(user.getBio()).isEqualTo("안녕하세요");
    }

    @Test
    void 빈_자기소개는_지우는_요청으로_보고_null로_저장한다() {
        User user = new User("user@test.com", "password", "user", "서울특별시 마포구");
        user.updateProfile(null, null, "안녕하세요");

        user.updateProfile(null, null, "  ");

        assertThat(user.getBio()).isNull();
    }

    @Test
    void 개인정보를_파기하면_자기소개도_지운다() {
        User user = new User("user@test.com", "password", "user", "서울특별시 마포구");
        user.updateProfile(null, null, "안녕하세요");

        user.purgePersonalData();

        assertThat(user.getBio()).isNull();
    }
}
