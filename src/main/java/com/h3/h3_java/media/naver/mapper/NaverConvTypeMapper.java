package com.h3.h3_java.media.naver.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Map;

@Mapper
public interface NaverConvTypeMapper {

    void insertStatreportLog(Map<String, Object> row);

    int countSuccessReport(@Param("customerId") String customerId, @Param("date") String date);
}
