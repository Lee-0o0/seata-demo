package org.example.orderservice.client;

import feign.Retryer;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.context.annotation.Bean;
import org.springframework.web.bind.annotation.*;

@FeignClient(name="account-service", url="${account.service-url}", configuration=AccountFeignClient.Configuration.class)
public interface AccountFeignClient {
    @PostMapping("/accounts/debit")
    void debit(@RequestBody DeductRequest request);
    @PostMapping("/accounts/compensate")
    void compensate(@RequestBody CompensateRequest request);
    record DeductRequest(String operationId, long userId, java.math.BigDecimal amount) {}
    record CompensateRequest(String operationId) {}
    class Configuration {
        @Bean
        public Retryer accountRetryer() { return Retryer.NEVER_RETRY; }
    }
}
