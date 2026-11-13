package org.example.orderservice.client;

import org.springframework.stereotype.Component;

@Component
public class StockClient {
    private final StockFeignClient client;
    public StockClient(StockFeignClient client) { this.client = client; }
    public void deduct(String operationId, long productId, int quantity) {
        try {
            client.deduct(new StockFeignClient.DeductRequest(operationId, productId, quantity));
        } catch (feign.FeignException failure) {
            // Feign Request 不可序列化；Saga 日志仅保存可持久化的异常，不保留原异常对象。
            throw new IllegalStateException("Stock service HTTP " + failure.status());
        }
    }
    public void compensate(String operationId) {
        try {
            client.compensate(new StockFeignClient.CompensateRequest(operationId));
        } catch (feign.FeignException failure) {
            // Feign Request 不可序列化；Saga 日志仅保存可持久化的异常，不保留原异常对象。
            throw new IllegalStateException("Stock service HTTP " + failure.status());
        }
    }
}
