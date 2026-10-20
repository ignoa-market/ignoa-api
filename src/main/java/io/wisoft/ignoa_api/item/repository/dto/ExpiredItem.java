package io.wisoft.ignoa_api.item.repository.dto;

import java.time.LocalDateTime;

public record ExpiredItem(Long id, LocalDateTime endAt) {
}
