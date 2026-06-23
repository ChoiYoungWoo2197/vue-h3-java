package com.h3.h3_java.api.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AdminMapper {
    int countByUserManager(@Param("userId") String userId);
    int countByShareManager(@Param("userId") String userId);
}
