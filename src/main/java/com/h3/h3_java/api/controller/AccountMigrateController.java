package com.h3.h3_java.api.controller;

import com.h3.h3_java.api.mapper.AccountMapper;
import com.h3.h3_java.raw.mongo.AccountMongoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;

/**
 * MySQL h3_account → MongoDB h3_account 일회성 이관 엔드포인트.
 * 이관 완료 후 이 파일을 삭제할 것.
 */
@Slf4j
@RestController
@RequestMapping("/api/collector")
@RequiredArgsConstructor
public class AccountMigrateController {

    private final AccountMapper accountMapper;
    private final AccountMongoService accountMongoService;

    @PostMapping("/account-migrate")
    public Map<String, Object> migrate() {
        log.info("[MIGRATE] h3_account MySQL → MongoDB 시작");

        List<Map<String, Object>> rows = accountMapper.selectAll();
        int total = rows.size(), success = 0, skip = 0;

        for (Map<String, Object> row : rows) {
            String userId = String.valueOf(row.get("user_id"));
            try {
                if (accountMongoService.existsByUserId(userId)) {
                    log.warn("[MIGRATE] user_id={} 이미 존재, 건너뜀", userId);
                    skip++;
                    continue;
                }
                accountMongoService.insert(row);
                success++;
            } catch (Exception e) {
                log.error("[MIGRATE] user_id={} 실패", userId, e);
                skip++;
            }
        }

        log.info("[MIGRATE] 완료 — 전체:{}, 성공:{}, 건너뜀/실패:{}", total, success, skip);
        return Map.of(
            "result",  "success",
            "total",   total,
            "success", success,
            "skipped", skip
        );
    }
}
