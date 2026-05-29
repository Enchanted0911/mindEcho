package com.mindecho.module.billing.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mindecho.module.billing.entity.PointTransaction;
import org.apache.ibatis.annotations.Mapper;

/**
 * 积分流水 Mapper
 */
@Mapper
public interface PointTransactionMapper extends BaseMapper<PointTransaction> {
}

