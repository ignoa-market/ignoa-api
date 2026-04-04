package io.wisoft.ignoa_api.user.entity;

import io.wisoft.ignoa_api.global.entity.BaseEntity;
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

    @Column(nullable = false, unique = true)
    private String email;

    @Column
    private String password;

    @Column(nullable = false, unique = true)
    private String nickname;

    @Column(nullable = false)
    private String address;

    @Column
    private String profileImageUrl;

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

    public void updateNickname(String nickname) {
        this.nickname = nickname;
    }

    public void updateAddress(String address) {
        this.address = address;
    }

    public void updateProfileImage(String profileImageUrl) {
        this.profileImageUrl = profileImageUrl;
    }

    public void withdraw() {
        this.email = "withdrawn_" + this.id + "@deleted.com";
        this.nickname = "탈퇴한 사용자" + this.id;
        this.address = "";
        this.password = null;
        this.deletedAt = LocalDateTime.now();
    }
}
