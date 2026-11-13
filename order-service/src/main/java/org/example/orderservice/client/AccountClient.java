package org.example.orderservice.client;

import org.springframework.stereotype.Component;

@Component
public class AccountClient {
    private final AccountFeignClient client;
    public AccountClient(AccountFeignClient client) { this.client = client; }
    public void debit(String operationId, long userId, java.math.BigDecimal amount) {
        try {
            client.debit(new AccountFeignClient.DeductRequest(operationId, userId, amount));
        } catch (feign.FeignException failure) {
            // Feign Request 不可序列化；Saga 日志仅保存可持久化的异常，不保留原异常对象。
            throw new IllegalStateException("Account service HTTP " + failure.status());
        }
    }
    public void compensate(String operationId) {
        try {
            client.compensate(new AccountFeignClient.CompensateRequest(operationId));
        } catch (feign.FeignException failure) {
            // Feign Request 不可序列化；Saga 日志仅保存可持久化的异常，不保留原异常对象。
            throw new IllegalStateException("Account service HTTP " + failure.status());
        }
    }
}
