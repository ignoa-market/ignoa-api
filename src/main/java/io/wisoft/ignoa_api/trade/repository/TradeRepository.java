package io.wisoft.ignoa_api.trade.repository;

import io.wisoft.ignoa_api.trade.entity.Trade;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TradeRepository extends JpaRepository<Trade, Long> {
}
