package com.h3.h3_java.media.kakao.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Map;

@Mapper
public interface KakaoSaBudgetAlarmMapper {

    /** h3_budget_daily_alarm에서 KPI 알람 설정 조회 (im, clk, cv, cr 컬럼) */
    Map<String, Object> selectBudgetAlarm(@Param("userId") String userId);

    /** h3_budget_daily_alarm.type 조회 (null이면 캠페인 레벨, 값 있으면 계정 레벨) */
    String selectAlarmType(@Param("userId") String userId);
}
