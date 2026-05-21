package com.h3.h3_java.queue.message;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CollectorMessage {
    private String media;
    private String type;
    private String userId;
    private String customerId;
    private boolean force;
    private String fromDate;   // null = 자동 날짜
    private String toDate;

    public CollectorMessage(String media, String type, String userId, String customerId, boolean force) {
        this(media, type, userId, customerId, force, null, null);
    }
}