package org.example.stockservice.controller;

import org.example.stockservice.service.StockService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/stocks")
public class StockController {
    private final StockService service;

    public StockController(StockService service) {
        this.service = service;
    }

    @GetMapping("/{productId}")
    public StockResponse get(@PathVariable long productId) {
        return new StockResponse(productId, service.getQuantity(productId));
    }

    @PostMapping("/deduct")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deduct(@RequestBody DeductRequest request) {
        if (request.productId() == null || request.quantity() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "商品 ID 和扣减数量不能为空");
        }
        service.deduct(request.productId(), request.quantity());
    }

    public record DeductRequest(Long productId, Integer quantity) {}
    public record StockResponse(long productId, int quantity) {}
}
