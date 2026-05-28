package com.h3.h3_java.media.kakao.mapper;

import com.h3.h3_java.media.kakao.dto.KakaoSaAccountDto;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;
import java.util.Map;

@Mapper
public interface KakaoSaMapper {

    List<KakaoSaAccountDto> selectKakaoSaAccounts();

    Map<String, Object> selectLatestKakaoSaToken();
}
