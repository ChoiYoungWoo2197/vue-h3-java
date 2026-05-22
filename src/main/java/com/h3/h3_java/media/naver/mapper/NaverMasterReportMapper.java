package com.h3.h3_java.media.naver.mapper;

import com.h3.h3_java.media.naver.dto.NaverAccountDto;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface NaverMasterReportMapper {

    List<NaverAccountDto> selectNaverAccounts();
}