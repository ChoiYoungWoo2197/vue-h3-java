package com.h3.h3_java.media.naver.mapper;

import org.apache.ibatis.annotations.Mapper;

import java.util.Map;

@Mapper
public interface NaverStateReportMapper {

    void insertStatreportLog(Map<String, Object> row);
}
