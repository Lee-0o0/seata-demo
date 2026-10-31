package org.example.stockservice.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface StockMapper {
    @Select("SELECT quantity FROM stock WHERE product_id = #{productId}")
    Integer findQuantity(@Param("productId") long productId);

    @Update("UPDATE stock SET quantity = quantity - #{quantity} WHERE product_id = #{productId} AND quantity >= #{quantity}")
    int deduct(@Param("productId") long productId, @Param("quantity") int quantity);
}
