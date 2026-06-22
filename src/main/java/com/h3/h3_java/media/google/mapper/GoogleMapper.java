package com.h3.h3_java.media.google.mapper;

import com.h3.h3_java.media.google.dto.GoogleAccountDto;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface GoogleMapper {

    List<GoogleAccountDto> selectGoogleAccounts();

    // ── Token (migration only) ────────────────────────────────────────────────

    String selectGoogleAccessToken();
    String selectGoogleRefreshToken();
}
