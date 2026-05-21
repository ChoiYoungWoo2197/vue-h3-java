package com.h3.h3_java.media.naver.mapper;

import com.h3.h3_java.media.naver.dto.NaverAdDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface NaverAdStatMapper {
    List<NaverAdDto> selectAdsByCustomer(@Param("customerId") String customerId);
    int countAdDailyData(@Param("customerId") String customerId, @Param("date") String date);
    void upsertAdDaily(Map<String, Object> row);
    void upsertAccountLogAd(@Param("customerId") String customerId, @Param("nowdate") String nowdate);
}
