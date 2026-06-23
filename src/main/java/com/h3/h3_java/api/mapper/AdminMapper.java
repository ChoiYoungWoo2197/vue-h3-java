package com.h3.h3_java.api.mapper;

import org.apache.ibatis.annotations.Mapper;

import java.util.List;
import java.util.Map;

@Mapper
public interface AdminMapper {
    /** 이관용: h3_share 전체 읽기 */
    List<Map<String, Object>> selectAllShare();

    /** 이관용: h3_adv 전체 읽기 */
    List<Map<String, Object>> selectAllAdv();
}
