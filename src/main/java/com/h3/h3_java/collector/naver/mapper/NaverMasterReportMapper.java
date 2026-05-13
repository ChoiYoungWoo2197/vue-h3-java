package com.h3.h3_java.collector.naver.mapper;

import com.h3.h3_java.collector.naver.dto.NaverAccountDto;
import com.h3.h3_java.collector.naver.dto.NaverDeltaDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface NaverMasterReportMapper {

    List<NaverAccountDto> selectNaverAccounts();

    NaverDeltaDto selectNaverDelta(@Param("deltakey") String deltakey,
                                   @Param("name") String name,
                                   @Param("userid") String userid);

    int countNaverDelta(@Param("deltakey") String deltakey,
                        @Param("name") String name,
                        @Param("userid") String userid);

    void insertNaverDelta(NaverDeltaDto dto);

    void updateNaverDelta(NaverDeltaDto dto);

    void updateNaverDeltaFail(@Param("deltakey") String deltakey,
                               @Param("name") String name,
                               @Param("userid") String userid);
}