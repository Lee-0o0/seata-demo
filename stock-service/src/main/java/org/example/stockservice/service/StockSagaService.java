package org.example.stockservice.service;

import java.math.BigDecimal;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@Service
public class StockSagaService {
    private final JdbcTemplate jdbc;
    public StockSagaService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional
    public void deduct(String operationId, Long resourceId, Integer value) {
        validateId(operationId);
        if (resourceId == null || resourceId <= 0 || value == null || value <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "资源 ID 或扣减数值不合法");
        }
        // 唯一键上的写锁串行化同一操作的扣减与补偿，业务更新和流水同事务提交。
        jdbc.update("INSERT INTO stock_saga_operation(operation_id,status) VALUES(?,'NEW') ON DUPLICATE KEY UPDATE operation_id=operation_id", operationId);
        var row = jdbc.queryForMap("SELECT * FROM stock_saga_operation WHERE operation_id=? FOR UPDATE", operationId);
        String status = (String) row.get("status");
        if ("COMPENSATED".equals(status)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "操作已补偿，拒绝迟到的扣减");
        }
        if ("SUCCEEDED".equals(status)) {
            Number recordedId = (Number) row.get("product_id");
            Integer stored = (Integer) row.get("quantity");
            if (recordedId.longValue() != resourceId || !(stored.intValue() == value)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "操作 ID 已用于不同的扣减参数");
            }
            return;
        }
        if (jdbc.update("UPDATE stock SET quantity=quantity-? WHERE product_id=? AND quantity>=?", value, resourceId, value) != 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "资源不存在或可用数值不足");
        }
        jdbc.update("UPDATE stock_saga_operation SET product_id=?,quantity=?,status='SUCCEEDED' WHERE operation_id=?", resourceId, value, operationId);
    }

    @Transactional
    public void compensate(String operationId) {
        validateId(operationId);
        // 没有执行过扣减也保留补偿墓碑，阻止后续迟到请求。
        jdbc.update("INSERT INTO stock_saga_operation(operation_id,status) VALUES(?,'COMPENSATED') ON DUPLICATE KEY UPDATE operation_id=operation_id", operationId);
        var row = jdbc.queryForMap("SELECT * FROM stock_saga_operation WHERE operation_id=? FOR UPDATE", operationId);
        if ("COMPENSATED".equals(row.get("status"))) { return; }
        if (!"SUCCEEDED".equals(row.get("status"))) { throw new IllegalStateException("操作状态异常"); }
        if (jdbc.update("UPDATE stock SET quantity=quantity+? WHERE product_id=?", row.get("quantity"), row.get("product_id")) != 1) {
            throw new IllegalStateException("补偿资源不存在");
        }
        jdbc.update("UPDATE stock_saga_operation SET status='COMPENSATED' WHERE operation_id=?", operationId);
    }

    private void validateId(String operationId) {
        if (operationId == null || !operationId.matches("[A-Za-z0-9:_-]{1,128}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "操作 ID 必须为 1 到 128 位字母、数字、冒号、下划线或短横线");
        }
    }
}
