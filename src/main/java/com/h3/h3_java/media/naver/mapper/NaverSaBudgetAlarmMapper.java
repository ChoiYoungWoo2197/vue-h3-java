package com.h3.h3_java.media.naver.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Map;

@Mapper
public interface NaverSaBudgetAlarmMapper {

    Map<String, Object> selectAlarmSetting(@Param("userId") String userId);

    int countRecentBizmoneyAlarm(@Param("userId") String userId,
                                 @Param("advid") String advid);

    int countRecentAlarm(@Param("userId") String userId,
                         @Param("advid") String advid,
                         @Param("level") String level,
                         @Param("targetId") String targetId,
                         @Param("type") String type);

    void insertBudgetAlarm(Map<String, Object> row);
}
