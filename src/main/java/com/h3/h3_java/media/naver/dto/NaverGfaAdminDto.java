package com.h3.h3_java.media.naver.dto;

import lombok.Data;

@Data
public class NaverGfaAdminDto {
    private String accountGfa;           // OAuth client_id
    private String accountNaverSecret;   // OAuth client_secret
    private String accountNaverCustomer; // AccessManagerAccountNo 헤더
}
