package com.h3.h3_java.media.naver.dto;

import lombok.Data;

@Data
public class NaverGfaAccountDto {
    private String userId;
    private String accountGfa; // GFA adAccount 번호 (advkey로도 사용)
}
