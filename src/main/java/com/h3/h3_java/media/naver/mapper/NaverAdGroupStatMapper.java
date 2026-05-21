package com.h3.h3_java.media.naver.mapper;

import com.h3.h3_java.media.naver.dto.NaverAdGroupDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface NaverAdGroupStatMapper {
    List<NaverAdGroupDto> selectAdGroupsByCustomer(@Param("customerId") String customerId);
    int countAdgroupDailyData(@Param("customerId") String customerId, @Param("date") String date);
    void upsertAdgroupDaily(Map<String, Object> row);
}
