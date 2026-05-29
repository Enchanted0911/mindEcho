package com.mindecho.module.memory.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mindecho.module.memory.entity.Memory;
import org.apache.ibatis.annotations.Mapper;

/**
 * 记忆 Mapper
 */
@Mapper
public interface MemoryMapper extends BaseMapper<Memory> {
}

