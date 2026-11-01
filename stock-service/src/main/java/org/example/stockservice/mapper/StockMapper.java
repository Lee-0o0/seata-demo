package org.example.stockservice.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface StockMapper {
    @Select("SELECT quantity FROM stock WHERE product_id = #{productId}")
    Integer findQuantity(@Param("productId") long productId);

}
