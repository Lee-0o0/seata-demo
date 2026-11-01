package org.example.accountservice.controller;

import java.math.BigDecimal;
import org.example.accountservice.service.AccountSagaService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/accounts")
public class AccountController {
    private final AccountSagaService saga;
    private final org.example.accountservice.mapper.AccountMapper mapper;
    public AccountController(AccountSagaService saga, org.example.accountservice.mapper.AccountMapper mapper) {
        this.saga = saga; this.mapper = mapper;
    }
    @PostMapping("/debit")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deduct(@RequestBody DeductRequest request) {
        saga.deduct(request.operationId(), request.userId(), request.amount());
    }
    @PostMapping("/compensate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void compensate(@RequestBody CompensateRequest request) { saga.compensate(request.operationId()); }
    @GetMapping("/{userId}")
    public org.example.accountservice.mapper.AccountMapper.Account get(@PathVariable long userId) {
        var account = mapper.find(userId);
        if (account == null) { throw new ResponseStatusException(HttpStatus.NOT_FOUND, "账户不存在"); }
        return account;
    }
    public record DeductRequest(String operationId, Long userId, BigDecimal amount) {}
    public record CompensateRequest(String operationId) {}

}
