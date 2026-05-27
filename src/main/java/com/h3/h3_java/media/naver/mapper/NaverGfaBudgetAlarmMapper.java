package com.h3.h3_java.media.naver.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Map;

@Mapper
public interface NaverGfaBudgetAlarmMapper {

    Map<String, Object> selectAlarmSetting(@Param("userId") String userId);

    int countRecentAlarm(@Param("userId") String userId,
                         @Param("advid") String advid,
                         @Param("level") String level,
                         @Param("targetId") String targetId,
                         @Param("type") String type);

    double sumGfaCampaignDaily(@Param("advid") String advid,
                               @Param("fromDate") String fromDate,
                               @Param("toDate") String toDate,
                               @Param("kpiColumn") String kpiColumn,
                               @Param("campaignId") String campaignId);

    void insertBudgetAlarm(Map<String, Object> row);
}
