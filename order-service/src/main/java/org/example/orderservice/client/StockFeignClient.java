package org.example.orderservice.client;

import feign.Retryer;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.context.annotation.Bean;
import org.springframework.web.bind.annotation.*;

@FeignClient(name="stock-service", url="${stock.service-url}", configuration=StockFeignClient.Configuration.class)
public interface StockFeignClient {
    @PostMapping("/stocks/deduct")
    void deduct(@RequestBody DeductRequest request);
    @PostMapping("/stocks/compensate")
    void compensate(@RequestBody CompensateRequest request);
    record DeductRequest(String operationId, long productId, int quantity) {}
    record CompensateRequest(String operationId) {}
    class Configuration {
        @Bean
        public Retryer stockRetryer() { return Retryer.NEVER_RETRY; }
    }
}
