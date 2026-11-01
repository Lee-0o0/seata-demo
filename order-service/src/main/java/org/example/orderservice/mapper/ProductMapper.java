package org.example.orderservice.mapper;
import java.math.BigDecimal;
import org.apache.ibatis.annotations.*;
@Mapper
public interface ProductMapper {
    @Select("SELECT price FROM product WHERE id=#{id}")
    BigDecimal price(long id);
}
