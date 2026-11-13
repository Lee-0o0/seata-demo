package org.example.orderservice.mapper;

import java.util.List;
import org.apache.ibatis.annotations.*;
import org.example.orderservice.entity.PurchaseOrder;

@Mapper
public interface OrderMapper {
    String COLUMNS = "id,user_id AS userId,product_id AS productId,quantity,unit_price AS unitPrice,total_amount AS totalAmount,created_at AS createdAt,request_id AS requestId,status,simulate_failure AS simulateFailure";
    @Insert("INSERT INTO purchase_order(user_id,product_id,quantity,unit_price,total_amount,created_at,request_id,status,simulate_failure) VALUES(#{userId},#{productId},#{quantity},#{unitPrice},#{totalAmount},#{createdAt},#{requestId},#{status},#{simulateFailure}) ON DUPLICATE KEY UPDATE id=purchase_order.id")
    int insert(PurchaseOrder order);
    @Select("SELECT " + COLUMNS + " FROM purchase_order WHERE id=#{id}")
    PurchaseOrder findById(long id);
    @Select("SELECT " + COLUMNS + " FROM purchase_order WHERE request_id=#{requestId}")
    PurchaseOrder findByRequestId(String requestId);
    @Update("UPDATE purchase_order SET status='CLOSED' WHERE request_id=#{requestId}")
    int closeOrder(String requestId);
    @Update("UPDATE purchase_order SET status=#{status} WHERE id=#{id} AND status IN ('PROCESSING','COMPENSATING')")
    int updateStatus(@Param("id") long id, @Param("status") String status);
    @Select("SELECT id FROM purchase_order WHERE status IN ('PROCESSING','COMPENSATING') ORDER BY id LIMIT 100")
    List<Long> pending();
}
