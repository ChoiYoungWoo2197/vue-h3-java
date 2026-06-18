package com.h3.h3_java.auth;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface UserMapper {
    UserDto selectByUserId(@Param("userId") String userId);
    List<UserDto> selectAll();
    void updateInfo(@Param("userId") String userId,
                    @Param("name") String name,
                    @Param("company") String company,
                    @Param("email") String email,
                    @Param("phone") String phone);
    void updatePassword(@Param("userId") String userId,
                        @Param("hashedPass") String hashedPass,
                        @Param("passupdate") String passupdate);
}
