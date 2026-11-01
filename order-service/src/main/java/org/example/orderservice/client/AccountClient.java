package org.example.orderservice.client;

import feign.FeignException;
import feign.RetryableException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class AccountClient {
    private final AccountFeignClient client;

    public AccountClient(AccountFeignClient client) {
        this.client = client;
    }

    public void debit(long orderId, long userId, java.math.BigDecimal amount) {
        try {
            client.debit(new AccountFeignClient.DebitRequest(orderId, userId, amount));
        } catch (RetryableException e) {
            // 超时结果可能未知，不自动重试扣减。
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "账户服务不可用或请求超时", e);
        } catch (FeignException e) {
            int status = e.status();
            if (status == 404) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "账户不存在", e);
            }
            if (status == 409) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "余额不足", e);
            }
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "账户服务响应异常", e);
        }
    }
}
