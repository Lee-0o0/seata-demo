package org.example.accountservice;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.CountDownLatch;
import org.springframework.web.server.ResponseStatusException;
import org.apache.seata.common.exception.SkipCallbackWrapperException;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.apache.seata.rm.fence.SpringFenceHandler;
import org.apache.seata.rm.tcc.api.BusinessActionContext;
import org.example.accountservice.tcc.AccountTccAction;
import org.springframework.boot.SpringApplication;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Integration checks against the running demo database, invoked by scripts/verify-tcc.py. */
public class FenceVerification {
    public static void main(String[] args) throws Exception {
        try (var application = SpringApplication.run(AccountServiceApplication.class, "--server.port=0")) {
            var dataSource = application.getBean(DataSource.class);
            var jdbc = new JdbcTemplate(dataSource);
            SpringFenceHandler.setDataSource(dataSource);
            SpringFenceHandler.setTransactionTemplate(new TransactionTemplate(
                    application.getBean(PlatformTransactionManager.class)));
            var fence = new SpringFenceHandler();
            var action = application.getBean(AccountTccAction.class);
            Method confirm = AccountTccAction.class.getMethod("confirm", BusinessActionContext.class);
            Method cancel = AccountTccAction.class.getMethod("cancel", BusinessActionContext.class);
            // 仅恢复此前中断验证留下的独立测试分支。
            for (var row : jdbc.queryForList("SELECT xid,branch_id FROM account_freeze WHERE xid LIKE 'balance-verification-%' AND status='TRIED'")) {
                var pending = context(row.get("xid").toString(), ((Number) row.get("branch_id")).longValue());
                check(fence.rollbackFence(cancel, action, pending.getXid(), pending.getBranchId(), new Object[]{pending}, "accountFreeze"),
                        "Interrupted test reservation could not be released");
            }
            var before = jdbc.queryForList("SELECT * FROM account ORDER BY user_id");
            for (String status : new String[]{"CONFIRMED", "CANCELLED"}) {
                Map<String, Object> row = jdbc.queryForMap(
                        "SELECT xid,branch_id FROM account_freeze WHERE status=? ORDER BY created_at DESC LIMIT 1", status);
                var context = context(row.get("xid").toString(), ((Number) row.get("branch_id")).longValue());
                for (int i = 0; i < 2; i++) {
                    boolean success = status.equals("CONFIRMED")
                            ? fence.commitFence(confirm, action, context.getXid(), context.getBranchId(), new Object[]{context})
                            : fence.rollbackFence(cancel, action, context.getXid(), context.getBranchId(), new Object[]{context}, "accountFreeze");
                    check(success, "Repeated phase two must succeed");
                }
            }
            check(before.equals(jdbc.queryForList("SELECT * FROM account ORDER BY user_id")), "Repeated callbacks changed balances");
            System.out.println("PASS Fence repeated Confirm/Cancel do not change balances");

            var empty = context("fence-verification-" + UUID.randomUUID(), 1);
            check(fence.rollbackFence(cancel, action, empty.getXid(), empty.getBranchId(), new Object[]{empty}, "accountFreeze"), "Empty rollback failed");
            check(jdbc.queryForObject("SELECT status FROM tcc_fence_log WHERE xid=? AND branch_id=1", Integer.class, empty.getXid()) == 4,
                    "Empty rollback must create suspended Fence");
            boolean blocked = false;
            try {
                fence.prepareFence(empty.getXid(), empty.getBranchId(), "accountFreeze", () -> {
                    throw new AssertionError("Late Try must never run");
                });
            } catch (RuntimeException expected) {
                blocked = true;
            }
            check(blocked, "Late Try must be rejected");
            check(before.equals(jdbc.queryForList("SELECT * FROM account ORDER BY user_id")), "Empty rollback changed balances");
            System.out.println("PASS Fence empty rollback and rejection of late Try");

            // 独立账户只够两次冻结，绕过库存 AT 锁竞争验证账户并发防超扣。
            long userId = System.currentTimeMillis();
            jdbc.update("INSERT INTO account(user_id,available_balance,frozen_balance) VALUES(?,40,0)", userId);
            var executor = Executors.newFixedThreadPool(8);
            var start = new CountDownLatch(1);
            var results = new ArrayList<Future<Boolean>>();
            try {
                for (int i = 0; i < 8; i++) {
                    long orderId = userId + i;
                    results.add(executor.submit(() -> {
                        start.await();
                        var trial = context("balance-verification-" + UUID.randomUUID(), 1);
                        try {
                            fence.prepareFence(trial.getXid(), trial.getBranchId(), "accountFreeze",
                                    () -> action.freeze(trial, orderId, userId, new BigDecimal("20.00")));
                        } catch (SkipCallbackWrapperException wrapped) {
                            check(wrapped.getCause() instanceof ResponseStatusException
                                    && ((ResponseStatusException) wrapped.getCause()).getStatusCode().value() == 409,
                                    "Unexpected rejection: " + wrapped);
                            check(fence.rollbackFence(cancel, action, trial.getXid(), trial.getBranchId(), new Object[]{trial}, "accountFreeze"),
                                    "Failed Try rollback failed");
                            return false;
                        }
                        check(fence.commitFence(confirm, action, trial.getXid(), trial.getBranchId(), new Object[]{trial}), "Confirm failed");
                        return true;
                    }));
                }
                start.countDown();
                int successes = 0;
                for (Future<Boolean> result : results) {
                    if (result.get()) { successes++; }
                }
                check(successes == 2, "Expected exactly two successful reservations, got " + successes);
                var account = application.getBean(org.example.accountservice.mapper.AccountMapper.class).find(userId);
                check(account.availableBalance().signum() == 0 && account.frozenBalance().signum() == 0, "Concurrent debit lost funds");
                System.out.println("PASS concurrent TCC: 40 balance, 8 attempts of 20, exactly 2 commits and 6 rejections");
            } finally {
                executor.shutdownNow();
            }
        }
    }

    private static BusinessActionContext context(String xid, long branch) {
        var context = new BusinessActionContext();
        context.setXid(xid);
        context.setBranchId(branch);
        context.setActionName("accountFreeze");
        return context;
    }
    private static void check(boolean condition, String message) {
        if (!condition) { throw new AssertionError(message); }
    }
}
