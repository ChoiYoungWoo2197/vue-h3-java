package com.h3.h3_java.media.naver.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface NaverStateReportMapper {

    void insertStatreportLog(Map<String, Object> row);

    int countSuccessReport(@Param("customerId") String customerId,
                           @Param("date") String date,
                           @Param("reportType") String reportType);

    List<String> selectJobIdsByDate(@Param("customerId") String customerId,
                                    @Param("date") String date);

    List<Map<String, Object>> selectKeywordMapping(@Param("customerId") String customerId);

    int countKeywordDailyData(@Param("customerId") String customerId, @Param("date") String date);

    void upsertKeywordDaily(Map<String, Object> row);

    int countTargetDailyData(@Param("customerId") String customerId, @Param("date") String date);

    void bulkUpsertTargetRows(@Param("rows") List<Map<String, Object>> rows);
}
