package com.h3.h3_java.media.kakao.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Map;

@Mapper
public interface KakaoMoBudgetAlarmMapper {

    Map<String, Object> selectBudgetAlarm(@Param("userId") String userId);

    String selectAlarmType(@Param("userId") String userId);
}
