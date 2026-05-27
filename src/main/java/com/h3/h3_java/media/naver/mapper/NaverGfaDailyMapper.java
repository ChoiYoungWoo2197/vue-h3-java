package com.h3.h3_java.media.naver.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Map;

@Mapper
public interface NaverGfaDailyMapper {

    boolean hasGfaCampaignDailyData(@Param("advid") String advid, @Param("date") String date);

    void upsertGfaCampaignDaily(Map<String, Object> row);

    boolean hasGfaAdgroupDailyData(@Param("advid") String advid, @Param("date") String date);

    void upsertGfaAdgroupDaily(Map<String, Object> row);
}
