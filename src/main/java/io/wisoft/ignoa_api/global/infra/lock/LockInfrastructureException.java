package io.wisoft.ignoa_api.global.infra.lock;

public class LockInfrastructureException extends RuntimeException {
  public LockInfrastructureException(String message, Throwable cause) {
    super(message, cause);
  }
}
