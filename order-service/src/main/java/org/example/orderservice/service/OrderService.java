package org.example.orderservice.service;

import java.time.LocalDateTime;
import org.apache.seata.spring.annotation.GlobalTransactional;
import org.example.orderservice.client.StockClient;
import org.example.orderservice.entity.PurchaseOrder;
import org.example.orderservice.mapper.OrderMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class OrderService {
    private final OrderMapper mapper;
    private final StockClient stockClient;

    public OrderService(OrderMapper mapper, StockClient stockClient) {
        this.mapper = mapper;
        this.stockClient = stockClient;
    }

    @GlobalTransactional(name = "create-order", rollbackFor = Exception.class, timeoutMills = 30000)
    @Transactional
    public PurchaseOrder create(Long userId, Long productId, Integer quantity, Boolean exception) {
        if (userId == null || userId <= 0 || productId == null || productId <= 0
                || quantity == null || quantity <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "用户 ID、商品 ID 和数量必须大于 0");
        }
        PurchaseOrder order = new PurchaseOrder();
        order.setUserId(userId);
        order.setProductId(productId);
        order.setQuantity(quantity);
        order.setCreatedAt(LocalDateTime.now().withNano(0));
        mapper.insert(order);
        // SCA 通过 Feign 传播 XID，订单与库存作为 XA 分支统一提交或回滚。
        stockClient.deduct(productId, quantity);

        if (exception){
            throw new RuntimeException("模拟下单异常，检查库存是否回滚");
        }
        return order;
    }

    public PurchaseOrder get(long id) {
        if (id <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "订单 ID 必须大于 0");
        }
        PurchaseOrder order = mapper.findById(id);
        if (order == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "订单不存在");
        }
        return order;
    }
}
