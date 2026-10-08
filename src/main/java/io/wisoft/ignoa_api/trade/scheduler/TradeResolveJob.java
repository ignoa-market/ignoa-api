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
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class TradeResolveJob {

    private static final Duration STUCK_THRESHOLD = Duration.ofMinutes(10);
    private static final int BATCH_SIZE = 100;

    private final TradeFacade tradeFacade;
    private final TradeRepository tradeRepository;

    public void resolve() {
        long startedAt = System.nanoTime();
        LocalDateTime cutoff = LocalDateTime.now().minus(STUCK_THRESHOLD);

        List<Trade> stuckTrades = tradeRepository.findStuckConfirming(
                cutoff, PageRequest.of(0, BATCH_SIZE)
        );

        if (stuckTrades.isEmpty()) {
            return;
        }

        int failedCount = 0;

        for (Trade trade : stuckTrades) {
            if (!resolveOne(trade)) {
                failedCount++;
            }
        }

        long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);

        if (failedCount > 0) {
            log.warn(
                    "결제 결과 미확정 거래 재확인 결과: target={}, failed={}, durationMs={}",
                    stuckTrades.size(),
                    failedCount,
                    durationMs
            );
        } else {
            log.info(
                    "결제 결과 미확정 거래 재확인 결과: target={}, failed=0, durationMs={}",
                    stuckTrades.size(),
                    durationMs
            );
        }
    }

    private boolean resolveOne(Trade trade) {
        try {
            tradeFacade.resolveStuck(trade);
            return true;

        } catch (Exception e) {
            log.debug(
                    "결제 결과 미확정 거래 재확인 실패: tradeId={}, orderId={}, errorType={}, action=다음 주기 재시도",
                    trade.getId(),
                    trade.getConfirmingOrderId(),
                    e.getClass().getSimpleName()
            );

            return false;
        }
    }
}
