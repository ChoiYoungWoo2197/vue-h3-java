package com.h3.h3_java.api.collector;

import com.h3.h3_java.api.mapper.AccountMapper;
import com.h3.h3_java.raw.mongo.AccountMongoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.web.bind.annotation.GetMapping;
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
                .filter(row -> {
                    String uid = String.valueOf(row.get("user_id"));
                    return !mongoUserIds.contains(uid);
                })
                .map(row -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("user_id",              row.get("user_id"));
                    m.put("account_naver",         row.get("account_naver"));
                    m.put("account_naver_customer",row.get("account_naver_customer"));
                    m.put("account_gfa",           row.get("account_gfa"));
                    m.put("account_kakaosa",       row.get("account_kakaosa"));
                    m.put("account_kakaomoment",   row.get("account_kakaomoment"));
                    m.put("account_google",        row.get("account_google"));
                    m.put("account_regdate",       row.get("account_regdate"));
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
}
