package io.wisoft.ignoa_api.global.infra.metrics;

import io.wisoft.ignoa_api.global.infra.lock.LockOperation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface MeasureTransaction {

    LockOperation operation();
}
