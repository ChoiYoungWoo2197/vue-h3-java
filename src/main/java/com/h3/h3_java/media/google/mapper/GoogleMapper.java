package com.h3.h3_java.media.google.mapper;

import com.h3.h3_java.media.google.dto.GoogleAccountDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface GoogleMapper {

    List<GoogleAccountDto> selectGoogleAccounts();

    // ── Token ─────────────────────────────────────────────────────────────────

    String selectGoogleAccessToken();
    String selectGoogleRefreshToken();
    void updateGoogleAccessToken(@Param("accessToken") String accessToken);
}
