package com.h3.h3_java.api.mapper;

import org.apache.ibatis.annotations.Mapper;

import java.util.List;
import java.util.Map;

@Mapper
public interface AccountLogMapper {

    List<Map<String, Object>> selectAll();
}
