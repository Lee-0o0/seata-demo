package org.example.accountservice.mapper;

import java.math.BigDecimal;
import org.apache.ibatis.annotations.*;

@Mapper
public interface AccountMapper {
    record Account(long userId, BigDecimal availableBalance, BigDecimal frozenBalance) {}
    record Freeze(String xid, long branchId, long orderId, long userId, BigDecimal amount, String status) {}

    @Select("SELECT user_id, available_balance, frozen_balance FROM account WHERE user_id=#{userId}")
    Account find(long userId);

    @Update("UPDATE account SET available_balance=available_balance-#{amount}, frozen_balance=frozen_balance+#{amount} WHERE user_id=#{userId} AND available_balance>=#{amount}")
    int freeze(@Param("userId") long userId, @Param("amount") BigDecimal amount);

    @Insert("INSERT INTO account_freeze(xid,branch_id,order_id,user_id,amount,status) VALUES(#{xid},#{branchId},#{orderId},#{userId},#{amount},'TRIED')")
    int insert(Freeze freeze);

    @Select("SELECT xid,branch_id,order_id,user_id,amount,status FROM account_freeze WHERE xid=#{xid} AND branch_id=#{branchId} FOR UPDATE")
    Freeze lock(@Param("xid") String xid, @Param("branchId") long branchId);

    @Update("UPDATE account SET frozen_balance=frozen_balance-#{amount} WHERE user_id=#{userId} AND frozen_balance>=#{amount}")
    int confirm(@Param("userId") long userId, @Param("amount") BigDecimal amount);

    @Update("UPDATE account SET available_balance=available_balance+#{amount}, frozen_balance=frozen_balance-#{amount} WHERE user_id=#{userId} AND frozen_balance>=#{amount}")
    int cancel(@Param("userId") long userId, @Param("amount") BigDecimal amount);

    @Update("UPDATE account_freeze SET status=#{status} WHERE xid=#{xid} AND branch_id=#{branchId} AND status='TRIED'")
    int finish(@Param("xid") String xid, @Param("branchId") long branchId, @Param("status") String status);
}
