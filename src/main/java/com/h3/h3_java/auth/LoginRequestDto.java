package com.h3.h3_java.auth;

import lombok.Data;

@Data
public class LoginRequestDto {
    private String userid;
    private String userpass;
}
