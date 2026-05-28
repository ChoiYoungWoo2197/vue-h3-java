package com.h3.h3_java.media.kakao.mapper;

import com.h3.h3_java.media.kakao.dto.KakaoMoAccountDto;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;
import java.util.Map;

@Mapper
public interface KakaoMoMapper {

    List<KakaoMoAccountDto> selectKakaoMoAccounts();

    Map<String, Object> selectLatestKakaoMoToken();
}
