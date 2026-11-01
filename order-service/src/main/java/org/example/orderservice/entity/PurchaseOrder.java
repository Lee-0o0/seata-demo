package org.example.orderservice.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class PurchaseOrder {
    private Long id;
    private Long userId;
    private Long productId;
    private Integer quantity;
    private java.math.BigDecimal unitPrice;
    private java.math.BigDecimal totalAmount;
    private LocalDateTime createdAt;
}
