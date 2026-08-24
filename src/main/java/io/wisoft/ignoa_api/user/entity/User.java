package io.wisoft.ignoa_api.user.entity;

import io.wisoft.ignoa_api.global.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
        name = "users",
        uniqueConstraints = @UniqueConstraint(columnNames = {"provider", "oauth_id"})
)
public class User extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true)
    private String email;

    @Column
    private String password;

    @Column(nullable = false, unique = true)
    private String nickname;

    @Column
    private String address;

    @Column(name = "profile_image_reference")
    private String profileImageReference;

    @Column(nullable = false)
    private String provider;

    @Column(name = "oauth_id")
    private String oauthId;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    public User(String email, String password, String nickname, String address) {
        this.email = email;
        this.password = password;
        this.nickname = nickname;
        this.address = address;
        this.provider = "LOCAL";
    }

    public static User ofKakao(String email, String nickname, String profileImageUrl, String oauthId) {
        User user = new User();
        user.email = email;
        user.nickname =nickname;
        user.profileImageReference = profileImageUrl;
        user.provider = "KAKAO";
        user.oauthId = oauthId;
        return user;
    }

    public void updateProfile(String nickname, String address) {
        if(nickname != null) this.nickname = nickname;
        if (address != null) this.address = address;
    }

    public void updateProfileImage(String profileImageReference) {
        this.profileImageReference = profileImageReference;
    }

    public void withdraw() {
        this.deletedAt = LocalDateTime.now();
    }

    public void purgePersonalData() {
        this.email = null;
        this.password = null;
        this.nickname = "탈퇴한 사용자_" + this.id;
        this.address = null;
        this.profileImageReference = null;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public void restore() {
        this.deletedAt = null;
    }
}
