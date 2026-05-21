package com.h3.h3_java.media.naver.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface NaverConvTypeMapper {

    void insertStatreportLog(Map<String, Object> row);

    int countSuccessReport(@Param("customerId") String customerId, @Param("date") String date);

    int countCampaignConvTypeData(@Param("customerId") String customerId, @Param("date") String date);
    int countAdgroupConvTypeData(@Param("customerId") String customerId, @Param("date") String date);
    int countKeywordConvTypeData(@Param("customerId") String customerId, @Param("date") String date);
    int countAdConvTypeData(@Param("customerId") String customerId, @Param("date") String date);

    void bulkUpsertCampaignConvType(@Param("rows") List<Map<String, Object>> rows);
    void bulkUpsertAdgroupConvType(@Param("rows") List<Map<String, Object>> rows);
    void bulkUpsertKeywordConvType(@Param("rows") List<Map<String, Object>> rows);
    void bulkUpsertAdConvType(@Param("rows") List<Map<String, Object>> rows);
}
