package com.h3.h3_java.api.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface AdminMapper {
    /** 이관용: h3_share 전체 읽기 */
    List<Map<String, Object>> selectAllShare();

    /** 이관용: h3_adv 전체 읽기 */
    List<Map<String, Object>> selectAllAdv();

    /** 매체별 일별 비용 합계 (daily_advid 기준) */
    List<Map<String, Object>> selectDailyCstByTable(@Param("tableName") String tableName,
                                                     @Param("fromDate")  String fromDate,
                                                     @Param("toDate")    String toDate);

    /** 통합 알림 (admin: 전체) */
    List<Map<String, Object>> selectAlarmForAdmin(@Param("start")   int start,
                                                   @Param("display") int display);
    long countAlarmForAdmin();

    /** 통합 알림 (marketer: advId 필터) */
    List<Map<String, Object>> selectAlarmForMarketer(@Param("advIds")  List<String> advIds,
                                                      @Param("start")   int start,
                                                      @Param("display") int display);
    long countAlarmForMarketer(@Param("advIds") List<String> advIds);
}
