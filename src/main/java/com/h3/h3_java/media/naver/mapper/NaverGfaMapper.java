package com.h3.h3_java.media.naver.mapper;

import com.h3.h3_java.media.naver.dto.NaverGfaAdminDto;
import org.apache.ibatis.annotations.Mapper;

import java.util.Map;

@Mapper
public interface NaverGfaMapper {

    NaverGfaAdminDto selectGfaAdminAccount();

    Map<String, Object> selectLatestNaverToken();
}
