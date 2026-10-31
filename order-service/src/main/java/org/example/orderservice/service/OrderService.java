package org.example.orderservice.service;

import java.time.LocalDateTime;
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

    @Transactional
    public PurchaseOrder create(Long userId, Long productId, Integer quantity) {
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
        // 库存拒绝或调用异常时，异常向外传播以回滚本地订单。
        // 本地事务无法撤销已经在库存服务提交的扣减。
        stockClient.deduct(productId, quantity);
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
