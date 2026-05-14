package com.h3.h3_java.media.naver.dto;

import lombok.Data;

@Data
public class NaverDeltaDto {
    private Long dailySeq;
    private String deltakey;
    private String delta;
    private String jobid;
    private String name;
    private String dailyUpdate;
    private String userid;
    private Boolean success;
}