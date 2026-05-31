package com.mindecho.module.astrology.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mindecho.module.astrology.entity.UserAstrology;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户星盘信息 Mapper
 */
@Mapper
public interface UserAstrologyMapper extends BaseMapper<UserAstrology> {
}

