package org.example.stockservice.service;

import org.example.stockservice.mapper.StockMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class StockService {
    private final StockMapper mapper;

    public StockService(StockMapper mapper) {
        this.mapper = mapper;
    }

    public int getQuantity(long productId) {
        if (productId <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "商品 ID 必须大于 0");
        }
        Integer quantity = mapper.findQuantity(productId);
        if (quantity == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "商品库存不存在");
        }
        return quantity;
    }

}
