package com.h3.h3_java.api.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface ReportMapper {
    List<Map<String, Object>> selectReports(@Param("userId") String userId,
                                             @Param("offset") int offset,
                                             @Param("limit") int limit);
    int countReports(@Param("userId") String userId);
    List<Map<String, Object>> selectAllReports();
    List<Map<String, Object>> selectReportExts(@Param("bId") long bId);
    int insertReport(Map<String, Object> params);
    int insertReportExt(Map<String, Object> params);
    int updatePdfDate(@Param("id") long id, @Param("pdfdate") String pdfdate);
}
