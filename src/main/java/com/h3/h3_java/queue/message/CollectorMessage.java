package com.h3.h3_java.queue.message;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CollectorMessage {
    private String media;      // NAVER, KAKAO, GOOGLE, GFA
    private String type;       // MASTER, DAILY
    private String userId;
    private String customerId;
    private boolean force;
}