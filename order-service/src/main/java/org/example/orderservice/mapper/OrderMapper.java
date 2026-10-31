package org.example.orderservice.mapper;

import org.apache.ibatis.annotations.*;
import org.example.orderservice.entity.PurchaseOrder;

@Mapper
public interface OrderMapper {
    @Insert("INSERT INTO purchase_order (user_id, product_id, quantity, created_at) VALUES (#{userId}, #{productId}, #{quantity}, #{createdAt})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(PurchaseOrder order);

    @Select("SELECT id, user_id AS userId, product_id AS productId, quantity, created_at AS createdAt FROM purchase_order WHERE id = #{id}")
    PurchaseOrder findById(@Param("id") long id);
}
