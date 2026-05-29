package com.mindecho.module.payment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mindecho.module.payment.entity.VipOrder;
import org.apache.ibatis.annotations.Mapper;

/**
 * 会员订单 Mapper
 */
@Mapper
public interface VipOrderMapper extends BaseMapper<VipOrder> {
}

