package io.wisoft.ignoa_api.global.infra.metrics;


import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Aspect
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 1)
@RequiredArgsConstructor
public class MeasureTransactionAspect {

    private final MeterRegistry meterRegistry;

    @Around(value = "@annotation(measureTransaction)")
    public Object measure(ProceedingJoinPoint joinPoint, MeasureTransaction measureTransaction) throws Throwable {
        Timer timer = Timer.builder("transaction.duration")
                .tag("operation", measureTransaction.operation().metricTag())
                .register(meterRegistry);

        Timer.Sample sample = Timer.start(meterRegistry);

        try {
            return joinPoint.proceed();
        } finally {
            sample.stop(timer);
        }
    }
}
