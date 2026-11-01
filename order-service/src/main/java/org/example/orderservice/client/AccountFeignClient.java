package org.example.orderservice.client;

import java.math.BigDecimal;
import feign.Retryer;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.context.annotation.Bean;
import org.springframework.web.bind.annotation.*;

@FeignClient(name = "account-service", url = "${account.service-url}",
        configuration = AccountFeignClient.Configuration.class)
public interface AccountFeignClient {
    @PostMapping("/accounts/debit")
    void debit(@RequestBody DebitRequest request);
    record DebitRequest(long orderId, long userId, BigDecimal amount) {}
    class Configuration {
        @Bean
        public Retryer accountRetryer() { return Retryer.NEVER_RETRY; }
    }
}
