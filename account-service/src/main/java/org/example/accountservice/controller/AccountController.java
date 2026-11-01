package org.example.accountservice.controller;

import java.math.BigDecimal;
import org.apache.seata.core.context.RootContext;
import org.example.accountservice.mapper.AccountMapper;
import org.example.accountservice.tcc.AccountTccAction;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/accounts")
public class AccountController {
    private final AccountTccAction action;
    private final AccountMapper mapper;
    public AccountController(AccountTccAction action, AccountMapper mapper) {
        this.action = action; this.mapper = mapper;
    }
    @PostMapping("/debit")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void debit(@RequestBody DebitRequest request) {
        if (!RootContext.inGlobalTransaction()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "缺少全局事务 XID");
        }
        if (!action.freeze(null, request.orderId(), request.userId(), request.amount())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "账户冻结失败");
        }
    }
    @GetMapping("/{userId}")
    public AccountMapper.Account get(@PathVariable long userId) {
        AccountMapper.Account account = mapper.find(userId);
        if (account == null) { throw new ResponseStatusException(HttpStatus.NOT_FOUND, "账户不存在"); }
        return account;
    }
    public record DebitRequest(long orderId, long userId, BigDecimal amount) {}
}
