package org.example.orderservice.controller;

import java.net.URI;
import org.example.orderservice.entity.PurchaseOrder;
import org.example.orderservice.service.OrderService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/orders")
public class OrderController {
    private final OrderService service;

    public OrderController(OrderService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<PurchaseOrder> create(@RequestBody CreateOrderRequest request) {
        PurchaseOrder order = service.create(
                request.userId(), request.productId(), request.quantity(), request.exception(), request.requestId());
        if (!"SUCCESS".equals(order.getStatus())) {
            return ResponseEntity.status("CLOSED".equals(order.getStatus()) ? 409 : 202)
                    .location(URI.create("/orders/" + order.getId())).body(order);
        }
        return ResponseEntity.created(URI.create("/orders/" + order.getId())).body(order);
    }

    @GetMapping("/{id}")
    public PurchaseOrder get(@PathVariable long id) {
        return service.get(id);
    }

    public record CreateOrderRequest(Long userId, Long productId, Integer quantity, Boolean exception, String requestId) {}
}
