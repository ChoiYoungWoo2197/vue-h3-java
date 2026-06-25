package com.h3.h3_java.api.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface AlarmMapper {

    List<Map<String, Object>> selectAlarmsForUser(Map<String, Object> params);
    int countAlarmsForUser(Map<String, Object> params);

    List<Map<String, Object>> selectAlarmsForAdmin(Map<String, Object> params);
    int countAlarmsForAdmin(Map<String, Object> params);

    Map<String, Object> selectAlarmSetting(@Param("userId") String userId);

    void insertAlarmSetting(Map<String, Object> params);
    void updateAlarmSetting(Map<String, Object> params);
}
