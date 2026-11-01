package org.example.stockservice.controller;

import java.math.BigDecimal;
import org.example.stockservice.service.StockSagaService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/stocks")
public class StockController {
    private final StockSagaService saga;
    private final org.example.stockservice.service.StockService service;
    public StockController(StockSagaService saga, org.example.stockservice.service.StockService service) {
        this.saga = saga; this.service = service;
    }
    @PostMapping("/deduct")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deduct(@RequestBody DeductRequest request) {
        saga.deduct(request.operationId(), request.productId(), request.quantity());
    }
    @PostMapping("/compensate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void compensate(@RequestBody CompensateRequest request) { saga.compensate(request.operationId()); }
    @GetMapping("/{productId}")
    public StockResponse get(@PathVariable long productId) {
        return new StockResponse(productId, service.getQuantity(productId));
    }
    public record DeductRequest(String operationId, Long productId, Integer quantity) {}
    public record CompensateRequest(String operationId) {}
    public record StockResponse(long productId, int quantity) {}
}
