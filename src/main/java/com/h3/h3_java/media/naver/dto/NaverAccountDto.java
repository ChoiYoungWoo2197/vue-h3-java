package com.h3.h3_java.media.naver.dto;

import lombok.Data;

@Data
public class NaverAccountDto {
    private String userId;
    private String accountNaverAccess;
    private String accountNaverSecret;
    private String accountNaverCustomer;
}