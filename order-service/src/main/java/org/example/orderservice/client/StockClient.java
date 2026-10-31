package org.example.orderservice.client;

import feign.FeignException;
import feign.RetryableException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class StockClient {
    private final StockFeignClient client;

    public StockClient(StockFeignClient client) {
        this.client = client;
    }

    public void deduct(long productId, int quantity) {
        try {
            client.deduct(new StockFeignClient.DeductRequest(productId, quantity));
        } catch (RetryableException e) {
            // 超时结果可能未知，不自动重试扣减。
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "库存服务不可用或请求超时", e);
        } catch (FeignException e) {
            int status = e.status();
            if (status == 404) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "商品库存不存在", e);
            }
            if (status == 409) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "库存不足", e);
            }
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "库存服务响应异常", e);
        }
    }
}
