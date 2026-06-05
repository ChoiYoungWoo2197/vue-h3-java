package com.h3.h3_java.api.mapper;

import com.h3.h3_java.api.dto.AccountDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AccountMapper {
    AccountDto selectByUserId(@Param("userId") String userId);
}
