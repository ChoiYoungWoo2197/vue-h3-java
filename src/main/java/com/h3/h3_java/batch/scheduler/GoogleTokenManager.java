package com.h3.h3_java.batch.scheduler;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.FileInputStream;
import java.util.List;

@Slf4j
@Getter
@Component
public class GoogleTokenManager {

    @Value("${google.service-account-path}")
    private String serviceAccountPath;

    @Value("${google.developer-token}")
    private String developerToken;

    @Value("${google.manager-id}")
    private String managerId;

    private GoogleCredentials credentials;
    private volatile String accessToken;

    @PostConstruct
    public void init() {
        try {
            FileInputStream fis = new FileInputStream(serviceAccountPath);
            credentials = ServiceAccountCredentials
                .fromStream(fis)
                .createScoped(List.of("https://www.googleapis.com/auth/adwords"));
            doRefresh();
        } catch (Exception e) {
            log.error("[GOOGLE][TOKEN] 서비스 계정 로드 실패 path={} err={}", serviceAccountPath, e.getMessage());
        }
    }

    @Scheduled(fixedDelay = 1_800_000, initialDelay = 120_000)
    public void autoRefresh() {
        doRefresh();
    }

    public void forceRefresh() {
        doRefresh();
    }

    public String getAccessToken() {
        return accessToken;
    }

    private void doRefresh() {
        if (credentials == null) {
            log.warn("[GOOGLE][TOKEN] 서비스 계정 미로드 - 갱신 불가");
            return;
        }
        try {
            credentials.refresh();
            accessToken = credentials.getAccessToken().getTokenValue();
            log.info("[GOOGLE][TOKEN] 서비스 계정 토큰 갱신 완료");
        } catch (Exception e) {
            log.error("[GOOGLE][TOKEN] 갱신 실패: {}", e.getMessage(), e);
        }
    }
}
