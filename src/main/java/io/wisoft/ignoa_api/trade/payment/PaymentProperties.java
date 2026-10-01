package io.wisoft.ignoa_api.trade.payment;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "payment")
public record PaymentProperties(
        String baseUrl,
        String internalApiKey
) {
    public static final String INTERNAL_API_KEY_HEADER = "X-Internal-Api-Key";
}
