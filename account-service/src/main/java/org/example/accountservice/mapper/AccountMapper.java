package org.example.accountservice.mapper;
import java.math.BigDecimal;
import org.apache.ibatis.annotations.*;
@Mapper
public interface AccountMapper {
    record Account(long userId, BigDecimal availableBalance) {}
    @Select("SELECT user_id, available_balance FROM account WHERE user_id=#{userId}")
    Account find(long userId);
}
