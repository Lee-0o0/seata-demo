package org.example.accountservice.tcc;

import java.math.BigDecimal;
import org.apache.seata.rm.tcc.api.*;

@LocalTCC
public interface AccountTccAction {
    @TwoPhaseBusinessAction(name = "accountFreeze", commitMethod = "confirm",
            rollbackMethod = "cancel", useTCCFence = true)
    boolean freeze(BusinessActionContext context,
                   @BusinessActionContextParameter(paramName = "orderId") long orderId,
                   @BusinessActionContextParameter(paramName = "userId") long userId,
                   @BusinessActionContextParameter(paramName = "amount") BigDecimal amount);
    boolean confirm(BusinessActionContext context);
    boolean cancel(BusinessActionContext context);
}
