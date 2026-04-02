package io.wisoft.ignoa_api.user.entity;

import io.wisoft.ignoa_api.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "users")
public class User extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false, unique = true)
    private String name;

    @Column(nullable = false)
    private String address;

    @Column
    private String profileImageUrl;

    public User(String email, String password, String name, String address) {
        this.email = email;
        this.password = password;
        this.name = name;
        this.address = address;
    }

    public void updateName(String name) {
        this.name = name;
    }

    public void updateAddress(String address) {
        this.address = address;
    }

    public void updateProfileImage(String profileImageUrl) {
        this.profileImageUrl = profileImageUrl;
    }

    public void withdraw() {
        this.email = "withdrawn_" + this.id + "@deleted.com";
        this.name = "탈퇴한 사용자" + this.id;
        this.address = "";
        this.password = "";
    }
}
