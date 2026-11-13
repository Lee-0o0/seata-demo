package org.example.orderservice.service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.apache.seata.saga.engine.StateMachineEngine;
import org.apache.seata.saga.statelang.domain.ExecutionStatus;
import org.apache.seata.saga.statelang.domain.StateMachineInstance;
import org.example.orderservice.entity.PurchaseOrder;
import org.example.orderservice.mapper.OrderMapper;
import org.example.orderservice.mapper.ProductMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class OrderService {
    private static final Logger LOG = LoggerFactory.getLogger(OrderService.class);
    private final OrderMapper mapper;
    private final ProductMapper products;
    private final StateMachineEngine engine;
    private final DataSource dataSource;

    public OrderService(OrderMapper mapper, ProductMapper products, StateMachineEngine engine, DataSource dataSource) {
        this.mapper = mapper; this.products = products; this.engine = engine; this.dataSource = dataSource;
    }

    // 入口只校验、准备状态机参数；订单由 CreateOrder 节点创建。
    public PurchaseOrder create(Long userId, Long productId, Integer quantity, Boolean exception, String requestId) {
        // 验证请求是否合理
        if (userId == null || userId <= 0 || productId == null || productId <= 0 || quantity == null || quantity <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "用户 ID、商品 ID 和数量必须大于 0");
        }
        if (requestId == null) { requestId = UUID.randomUUID().toString(); }
        if (!requestId.matches("[A-Za-z0-9:_-]{1,128}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请求 ID 格式不合法");
        }
        String sagaBusinessKey = businessKey(requestId);
        // 同一请求串行启动；锁名使用摘要以满足 MySQL 64 字符限制。
        String lockName = sagaBusinessKey.substring("order:".length());
        try (var connection = dataSource.getConnection()) {
            // 从mysql中获取锁
            try (var lock = connection.prepareStatement("SELECT GET_LOCK(?, 10)")) {
                lock.setString(1, lockName);
                try (var result = lock.executeQuery()) {
                    if (!result.next() || result.getInt(1) != 1) {
                        throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "请求正在处理中，请使用相同请求 ID 重试");
                    }
                }
            }
            try {
                // 获取订单
                var order = mapper.findByRequestId(requestId);
                if (order != null) {
                    // 之前已经用相同请求创建了订单，校验参数
                    validateSameRequest(order, userId, productId, quantity, exception);
                    return get(order.getId());
                }
                // 此时没有订单
                // 获取状态机实例
                var instance = instanceByKey(sagaBusinessKey);
                if (instance != null) {
                    // 状态机实例不为空，说明相同的请求已经启动了状态机
                    // 但此时还没有订单创建，校验参数后复用已有流程，避免重复启动，恢复由 TC 驱动。
                    // 已有流程会继续查订单，如果还不存在，不会继续启动状态机，而是由TC恢复
                    validateSameRequest(instance.getStartParams(), userId, productId, quantity, exception);
                } else {
                    // 订单不存在，状态机不存在，启动正常流程
                    // 校验
                    BigDecimal price = products.price(productId);
                    if (price == null) { throw new ResponseStatusException(HttpStatus.NOT_FOUND, "商品不存在"); }
                    BigDecimal total = price.multiply(BigDecimal.valueOf(quantity));
                    if (total.precision() > 19) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "订单金额超出范围");
                    }
                    // 金额使用十进制字符串，避免恢复反序列化时的浮点误差。
                    var params = new HashMap<String, Object>();
                    params.put("requestId", requestId); params.put("userId", userId);
                    params.put("productId", productId); params.put("quantity", quantity);
                    params.put("unitPrice", price.toPlainString()); params.put("totalAmount", total.toPlainString());
                    params.put("simulateFailure", Boolean.TRUE.equals(exception));
                    try {
                        // 启动状态机
                        // stateMachineName : 状态机名称，与json配置文件中的名称一致
                        // tenantId : 租户名称，自定义
                        // businessKey : 业务标识，用于关联业务数据与状态机执行实例
                        // startParams ：启动参数，传递给状态机中的各个节点使用
                        engine.startWithBusinessKey("orderSaga", "order", sagaBusinessKey, params);
                    } catch (RuntimeException failure) {
                        LOG.warn("请求 {} Saga 执行未完成，已持久化实例由 TC 恢复", requestId, failure);
                    }
                }
                // 创建之后查询，返回结果
                order = mapper.findByRequestId(requestId);
                if (order == null) {
                    throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "订单尚未创建，请使用相同请求 ID 重试");
                }
                return get(order.getId());
            } finally {
                // 释放锁
                try (var release = connection.prepareStatement("SELECT RELEASE_LOCK(?)")) {
                    release.setString(1, lockName); release.execute();
                }
            }
        } catch (java.sql.SQLException failure) {
            throw new IllegalStateException("订单 Saga 执行锁失败", failure);
        }
    }

    private void validateSameRequest(PurchaseOrder order, Long userId, Long productId, Integer quantity, Boolean exception) {
        if (!userId.equals(order.getUserId()) || !productId.equals(order.getProductId())
                || !quantity.equals(order.getQuantity()) || Boolean.TRUE.equals(exception) != Boolean.TRUE.equals(order.getSimulateFailure())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "请求 ID 已用于不同的订单参数");
        }
    }

    private void validateSameRequest(Map<String, Object> params, Long userId, Long productId,
            Integer quantity, Boolean exception) {
        if (params == null) {
            throw new IllegalStateException("Saga 实例缺少下单启动参数");
        }
        if (!sameNumber(params.get("userId"), userId) || !sameNumber(params.get("productId"), productId)
                || !sameNumber(params.get("quantity"), quantity)
                || Boolean.TRUE.equals(params.get("simulateFailure")) != Boolean.TRUE.equals(exception)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "请求 ID 已用于不同的订单参数");
        }
    }

    private boolean sameNumber(Object stored, Number requested) {
        // 持久化 JSON 反序列化可能将 Long 变为 Integer，按数值比较。
        return stored instanceof Number && new BigDecimal(stored.toString())
                .compareTo(new BigDecimal(requested.toString())) == 0;
    }

    private String businessKey(String requestId) {
        try {
            return "order:" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(requestId.getBytes(StandardCharsets.US_ASCII)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private StateMachineInstance instanceByKey(String sagaBusinessKey) {
        return engine.getStateMachineConfig().getStateLogRepository().getStateMachineInstanceByBusinessKey(sagaBusinessKey, "order");
    }

    private StateMachineInstance synchronizeStatus(PurchaseOrder order) {
        var instance = instanceByKey(businessKey(order.getRequestId()));
        if (instance == null) { return null; }
        if (ExecutionStatus.SU == instance.getCompensationStatus()) {
            // 状态机补偿状态已完成，关闭订单
            mapper.closeOrder(order.getRequestId());
        } else if (!instance.isRunning() && ExecutionStatus.SU == instance.getStatus()
                && instance.getCompensationStatus() == null) {
            // 状态机结束运行，并且补偿状态为空，表示成功
            mapper.updateStatus(order.getId(), "SUCCESS");
        } else if (instance.getCompensationStatus() != null || ExecutionStatus.UN == instance.getStatus()
                || ExecutionStatus.FA == instance.getStatus()) {
            // 状态机正在补偿
            mapper.updateStatus(order.getId(), "COMPENSATING");
        }
        return instance;
    }

    @Scheduled(fixedDelayString="${saga.recovery-delay-ms:5000}")
    public void recover() {
        // 已启动流程由 TC 恢复；这里只同步订单状态，不从订单表补启动流程。
        for (long id : mapper.pending()) {
            try { synchronizeStatus(mapper.findById(id)); }
            catch (RuntimeException failure) { LOG.error("订单 {} Saga 状态同步失败", id, failure); }
        }
    }

    public PurchaseOrder get(long id) {
        if (id <= 0) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "订单 ID 必须大于 0"); }
        PurchaseOrder order = mapper.findById(id);
        if (order == null) { throw new ResponseStatusException(HttpStatus.NOT_FOUND, "订单不存在"); }
        var instance = synchronizeStatus(order);
        order = mapper.findById(id);
        // 订单关闭节点可能先于其他补偿完成；响应仍应展示整个流程的状态。
        if (instance != null && ExecutionStatus.SU != instance.getCompensationStatus()
                && (instance.getCompensationStatus() != null || ExecutionStatus.UN == instance.getStatus()
                    || ExecutionStatus.FA == instance.getStatus())) {
            order.setStatus("COMPENSATING");
        } else if (instance != null && instance.isRunning() && "SUCCESS".equals(order.getStatus())) {
            order.setStatus("PROCESSING");
        }
        return order;
    }
}
