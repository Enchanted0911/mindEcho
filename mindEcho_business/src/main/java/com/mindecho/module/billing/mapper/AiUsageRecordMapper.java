package com.mindecho.module.billing.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mindecho.module.billing.entity.AiUsageRecord;
import org.apache.ibatis.annotations.Mapper;

/**
 * AI 使用量记录 Mapper
 */
@Mapper
public interface AiUsageRecordMapper extends BaseMapper<AiUsageRecord> {
}

