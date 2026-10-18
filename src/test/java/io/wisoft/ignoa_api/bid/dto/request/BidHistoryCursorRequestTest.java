package io.wisoft.ignoa_api.bid.dto.request;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class BidHistoryCursorRequestTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void size를_보내지_않으면_20이다() {
        // When
        BidHistoryCursorRequest request = new BidHistoryCursorRequest(null, null);

        // Then
        assertThat(request.size()).isEqualTo(20);
        assertThat(validator.validate(request)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 51})
    void size가_1_미만이거나_50_초과면_검증에_실패한다(int size) {
        // When & Then
        assertThat(validator.validate(new BidHistoryCursorRequest(null, size))).hasSize(1);
    }

    @Test
    void 기준_입찰_ID가_1_미만이면_검증에_실패한다() {
        // When & Then
        assertThat(validator.validate(new BidHistoryCursorRequest(0L, 20))).hasSize(1);
    }
}
