package com.h3.h3_java.auth;

import lombok.Data;

@Data
public class UserDto {
    private String userId;
    private String userName;
    private String userPass;
    private String userEmail;
    private String userCompany;
    private String userPhone;
    private Integer userLevel;
    private String userStatus;
}
