package io.wisoft.ignoa_api.trade.scheduler;

import io.wisoft.ignoa_api.trade.entity.Trade;
import io.wisoft.ignoa_api.trade.repository.TradeRepository;
import io.wisoft.ignoa_api.trade.service.TradeFacade;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class TradeResolveJob {

    private static final Duration STUCK_THRESHOLD = Duration.ofMinutes(10);
    private static final int BATCH_SIZE = 100;

    private final TradeFacade tradeFacade;
    private final TradeRepository tradeRepository;

    public void resolve() {
        LocalDateTime cutoff = LocalDateTime.now().minus(STUCK_THRESHOLD);

        List<Trade> stuckTrades = tradeRepository.findStuckConfirming(
                cutoff, PageRequest.of(0, BATCH_SIZE)
        );

        for (Trade trade : stuckTrades) {
            resolveOne(trade);
        }
    }

    private void resolveOne(Trade trade) {
        try {
            tradeFacade.resolveStuck(trade);
        } catch (Exception e) {
            log.warn("멈춘 거래 복구 실패: tradeId={}, orderId={}, reason=다음 주기에 재시도",
                    trade.getId(), trade.getConfirmingOrderId(), e);
        }
    }
}
