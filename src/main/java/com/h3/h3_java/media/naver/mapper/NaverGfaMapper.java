package com.h3.h3_java.media.naver.mapper;

import com.h3.h3_java.media.naver.dto.NaverGfaAccountDto;
import com.h3.h3_java.media.naver.dto.NaverGfaAdminDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface NaverGfaMapper {

    NaverGfaAdminDto selectGfaAdminAccount();

    Map<String, Object> selectLatestNaverToken();

    void updateNaverToken(@Param("accessToken") String accessToken,
                          @Param("refreshToken") String refreshToken);

    List<NaverGfaAccountDto> selectGfaAccounts();
}
