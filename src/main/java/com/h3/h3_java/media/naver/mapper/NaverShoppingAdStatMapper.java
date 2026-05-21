package com.h3.h3_java.media.naver.mapper;

import com.h3.h3_java.media.naver.dto.NaverShoppingAdDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface NaverShoppingAdStatMapper {
    List<NaverShoppingAdDto> selectShoppingAdsByCustomer(@Param("customerId") String customerId);
    void upsertShoppingAdDaily(Map<String, Object> row);
    void upsertAccountLogShopping(@Param("customerId") String customerId, @Param("nowdate") String nowdate);
}
