package com.h3.h3_java.auth;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface UserMapper {
    UserDto selectByUserId(@Param("userId") String userId);
}
