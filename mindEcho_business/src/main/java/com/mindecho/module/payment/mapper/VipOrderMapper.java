package com.mindecho.module.payment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mindecho.module.payment.entity.VipOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 会员订单 Mapper
 */
@Mapper
public interface VipOrderMapper extends BaseMapper<VipOrder> {

    /**
     * 根据订单号查询
     */
    @Select("SELECT * FROM vip_order WHERE order_no = #{orderNo} AND deleted = 0")
    VipOrder findByOrderNo(@Param("orderNo") String orderNo);

    /**
     * 查询用户最新有效订单
     */
    @Select("SELECT * FROM vip_order WHERE user_id = #{userId} AND status = 'paid' AND deleted = 0 ORDER BY created_time DESC LIMIT 1")
    VipOrder findLatestPaidOrder(@Param("userId") Long userId);
}

