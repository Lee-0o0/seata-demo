package org.example.accountservice.tcc;

import java.math.BigDecimal;
import org.apache.seata.rm.tcc.api.BusinessActionContext;
import org.example.accountservice.mapper.AccountMapper;
import org.example.accountservice.mapper.AccountMapper.Freeze;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AccountTccActionImpl implements AccountTccAction {
    private final AccountMapper mapper;
    public AccountTccActionImpl(AccountMapper mapper) { this.mapper = mapper; }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean freeze(BusinessActionContext context, long orderId, long userId, BigDecimal amount) {
        if (context == null || context.getXid() == null || context.getBranchId() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "账户扣款必须在全局事务中调用");
        }
        if (orderId <= 0 || userId <= 0 || amount == null || amount.signum() <= 0
                || amount.scale() > 2 || amount.precision() > 19) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "订单、用户或金额不合法");
        }
        if (mapper.freeze(userId, amount) != 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "账户不存在或可用余额不足");
        }
        mapper.insert(new Freeze(context.getXid(), context.getBranchId(), orderId, userId, amount, "TRIED"));
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean confirm(BusinessActionContext context) { return finish(context, true); }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean cancel(BusinessActionContext context) { return finish(context, false); }

    private boolean finish(BusinessActionContext context, boolean commit) {
        Freeze freeze = mapper.lock(context.getXid(), context.getBranchId());
        // Try 本地事务失败时可能没有冻结记录；Cancel 无需操作余额。
        if (freeze == null) { return !commit; }
        String target = commit ? "CONFIRMED" : "CANCELLED";
        if (target.equals(freeze.status())) { return true; }
        if (!"TRIED".equals(freeze.status())) { return false; }
        int changed = commit ? mapper.confirm(freeze.userId(), freeze.amount())
                             : mapper.cancel(freeze.userId(), freeze.amount());
        if (changed != 1 || mapper.finish(freeze.xid(), freeze.branchId(), target) != 1) {
            throw new IllegalStateException("冻结记录与账户余额不一致，需要重试或排查");
        }
        return true;
    }
}
