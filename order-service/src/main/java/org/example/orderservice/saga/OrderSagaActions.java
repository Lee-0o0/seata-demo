package org.example.orderservice.saga;

import org.example.orderservice.client.AccountClient;
import org.example.orderservice.client.StockClient;
import org.example.orderservice.mapper.OrderMapper;
import org.springframework.stereotype.Component;

@Component("orderSagaActions")
public class OrderSagaActions {
    private final StockClient stock;
    private final AccountClient account;
    private final OrderMapper orders;
    public OrderSagaActions(StockClient stock, AccountClient account, OrderMapper orders) {
        this.stock = stock; this.account = account; this.orders = orders;
    }
    private org.example.orderservice.entity.PurchaseOrder snapshot(String requestId, Long userId,
            Long productId, Integer quantity, String unitPrice, String totalAmount, Boolean simulateFailure) {
        var order = new org.example.orderservice.entity.PurchaseOrder();
        order.setRequestId(requestId); order.setUserId(userId); order.setProductId(productId);
        order.setQuantity(quantity); order.setUnitPrice(new java.math.BigDecimal(unitPrice));
        order.setTotalAmount(new java.math.BigDecimal(totalAmount)); order.setSimulateFailure(simulateFailure);
        order.setCreatedAt(java.time.LocalDateTime.now().withNano(0));
        return order;
    }

    // 创建订单
    public boolean createOrder(String requestId, Long userId, Long productId, Integer quantity,
            String unitPrice, String totalAmount, Boolean simulateFailure) {
        var order = snapshot(requestId, userId, productId, quantity, unitPrice, totalAmount, simulateFailure);
        order.setStatus("PROCESSING");
        orders.insert(order);
        if ("CLOSED".equals(orders.findByRequestId(requestId).getStatus())) {
            throw new IllegalStateException("订单已关闭");
        }
        return true;
    }

    // 关闭订单
    @org.springframework.transaction.annotation.Transactional
    public boolean closeOrder(String requestId, Long userId, Long productId, Integer quantity,
            String unitPrice, String totalAmount, Boolean simulateFailure) {
        // 空补偿也留下关闭记录；事务及唯一键保证迟到的创建不能重新打开订单。
        var order = snapshot(requestId, userId, productId, quantity, unitPrice, totalAmount, simulateFailure);
        order.setStatus("CLOSED");
        orders.insert(order);
        orders.closeOrder(requestId);
        return true;
    }

    // 扣减余额
    public boolean deductStock(String requestId) {
        var order = orders.findByRequestId(requestId);
        stock.deduct("order:" + order.getId() + ":stock", order.getProductId(), order.getQuantity());
        return true;
    }

    // 还回余额
    public boolean debitAccount(String requestId) {
        var order = orders.findByRequestId(requestId);
        account.debit("order:" + order.getId() + ":account", order.getUserId(), order.getTotalAmount());
        return true;
    }

    // 扣减库存
    public boolean compensateStock(String requestId) {
        stock.compensate("order:" + orders.findByRequestId(requestId).getId() + ":stock");
        return true;
    }

    //还回库存
    public boolean compensateAccount(String requestId) {
        account.compensate("order:" + orders.findByRequestId(requestId).getId() + ":account");
        return true;
    }

    // 确认订单
    public boolean confirmOrder(String requestId) {
        if (Boolean.TRUE.equals(orders.findByRequestId(requestId).getSimulateFailure())) {
            throw new IllegalStateException("模拟扣减完成后的订单异常");
        }
        orders.updateStatus(orders.findByRequestId(requestId).getId(), "SUCCESS");
        if (!"SUCCESS".equals(orders.findByRequestId(requestId).getStatus())) {
            throw new IllegalStateException("已关闭订单不能确认");
        }
        return true;
    }
}
