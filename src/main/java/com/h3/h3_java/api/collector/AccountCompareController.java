package com.h3.h3_java.api.collector;

import com.h3.h3_java.api.mapper.AccountMapper;
import com.h3.h3_java.raw.mongo.AccountMongoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/collector")
@RequiredArgsConstructor
public class AccountCompareController {

    private final AccountMapper       accountMapper;
    private final AccountMongoService accountMongo;

    /**
     * MySQL h3_account에 있으나 MongoDB h3_account에 없는 계정 목록 반환
     * GET /api/collector/account-compare
     */
    @GetMapping("/account-compare")
    public Map<String, Object> compare() {
        List<Map<String, Object>> mysqlRows = accountMapper.selectAll();
        List<Document>            mongoDocs = accountMongo.findAll();

        Set<String> mongoUserIds = mongoDocs.stream()
                .map(d -> d.getString("user_id"))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        List<Map<String, Object>> missing = mysqlRows.stream()
                .filter(row -> !mongoUserIds.contains(String.valueOf(row.get("user_id"))))
                .map(row -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("user_id",               row.get("user_id"));
                    m.put("account_naver",          row.get("account_naver"));
                    m.put("account_naver_customer", row.get("account_naver_customer"));
                    m.put("account_gfa",            row.get("account_gfa"));
                    m.put("account_kakaosa",        row.get("account_kakaosa"));
                    m.put("account_kakaomoment",    row.get("account_kakaomoment"));
                    m.put("account_google",         row.get("account_google"));
                    m.put("account_regdate",        row.get("account_regdate"));
                    return m;
                })
                .collect(Collectors.toList());

        log.info("[ACCOUNT-COMPARE] MySQL={} Mongo={} 누락={}", mysqlRows.size(), mongoDocs.size(), missing.size());

        return Map.of(
                "mysql_total",  mysqlRows.size(),
                "mongo_total",  mongoDocs.size(),
                "missing_count",missing.size(),
                "missing",      missing
        );
    }

    /**
     * MySQL h3_account 기준으로 MongoDB에 없는 계정을 일괄 upsert
     * POST /api/collector/account-sync
     */
    @PostMapping("/account-sync")
    public Map<String, Object> sync() {
        List<Map<String, Object>> mysqlRows = accountMapper.selectAll();
        List<Document>            mongoDocs = accountMongo.findAll();

        Set<String> mongoUserIds = mongoDocs.stream()
                .map(d -> d.getString("user_id"))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        int synced = 0, skipped = 0, failed = 0;
        List<String> syncedIds = new ArrayList<>();

        for (Map<String, Object> row : mysqlRows) {
            String userId = String.valueOf(row.get("user_id"));
            if (mongoUserIds.contains(userId)) { skipped++; continue; }

            try {
                String naverid    = str(row.get("account_naver"));
                String customer   = str(row.get("account_naver_customer"));
                String access     = str(row.get("account_naver_access"));
                String secret     = str(row.get("account_naver_secret"));
                String gfa        = str(row.get("account_gfa"));
                String kakaosa    = str(row.get("account_kakaosa"));
                String kakaomo    = str(row.get("account_kakaomoment"));
                String google     = str(row.get("account_google"));

                if (notEmpty(naverid) || notEmpty(customer) || notEmpty(access) || notEmpty(secret)) {
                    accountMongo.upsertNaver(userId, naverid, customer, access, secret);
                }
                if (notEmpty(gfa))     accountMongo.upsertNaverDa(userId, gfa);
                if (notEmpty(kakaosa)) accountMongo.upsertKakaoSa(userId, kakaosa);
                if (notEmpty(kakaomo)) accountMongo.upsertKakaoMo(userId, kakaomo);
                if (notEmpty(google))  accountMongo.upsertGoogle(userId, google);

                syncedIds.add(userId);
                synced++;
                log.info("[ACCOUNT-SYNC] 동기화 완료 userId={}", userId);
            } catch (Exception e) {
                log.error("[ACCOUNT-SYNC] 실패 userId={}", userId, e);
                failed++;
            }
        }

        return Map.of(
                "mysql_total", mysqlRows.size(),
                "synced",      synced,
                "skipped",     skipped,
                "failed",      failed,
                "synced_ids",  syncedIds
        );
    }

    private String str(Object v) {
        return v == null ? "" : v.toString();
    }

    private boolean notEmpty(String v) {
        return v != null && !v.isBlank();
    }
}
