package com.h3.h3_java.api.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Map;

@Mapper
public interface AccountLogMapper {

    Map<String, Object> findByAdvkey(@Param("advkey") String advkey);
}
