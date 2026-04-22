package io.wisoft.ignoa_api;

import io.wisoft.ignoa_api.auth.jwt.JwtProperties;
import io.wisoft.ignoa_api.auth.oauth.KakaoProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@EnableConfigurationProperties({JwtProperties.class, KakaoProperties.class})
@SpringBootApplication
public class IgnoaApiApplication {

	public static void main(String[] args) {
		SpringApplication.run(IgnoaApiApplication.class, args);
	}

}
