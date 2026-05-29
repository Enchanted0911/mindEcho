package com.mindecho.module.payment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mindecho.module.payment.entity.PointOrder;
import org.apache.ibatis.annotations.Mapper;

/**
 * 积分充值订单 Mapper
 */
@Mapper
public interface PointOrderMapper extends BaseMapper<PointOrder> {
}

