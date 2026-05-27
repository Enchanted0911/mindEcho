package com.mindecho.module.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mindecho.module.auth.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/**
 * 用户 Mapper
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {

    /**
     * 根据 openid 查询用户
     */
    @Select("SELECT * FROM user WHERE openid = #{openid} AND deleted = 0")
    User findByOpenid(String openid);
}

