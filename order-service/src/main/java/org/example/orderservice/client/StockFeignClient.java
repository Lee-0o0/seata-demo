package org.example.orderservice.client;

import feign.Retryer;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.context.annotation.Bean;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "stock-service", url = "${stock.service-url}",
        configuration = StockFeignClient.Configuration.class)
public interface StockFeignClient {

    @PostMapping(value = "/stocks/deduct", consumes = "application/json")
    void deduct(@RequestBody DeductRequest request);

    record DeductRequest(long productId, int quantity) {}

    // 只作用于库存客户端，避免对非幂等扣减自动重试。
    class Configuration {
        @Bean
        public Retryer stockRetryer() {
            return Retryer.NEVER_RETRY;
        }
    }
}
