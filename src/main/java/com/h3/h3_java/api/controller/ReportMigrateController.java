package com.h3.h3_java.api.controller;

import com.h3.h3_java.api.mapper.ReportMapper;
import com.h3.h3_java.raw.mongo.ReportMongoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;

/**
 * MySQL h3_report + h3_report_ext → MongoDB h3_report 일회성 이관 엔드포인트.
 * 이관 완료 후 이 파일을 삭제할 것.
 */
@Slf4j
@RestController
@RequestMapping("/api/collector")
@RequiredArgsConstructor
public class ReportMigrateController {

    private final ReportMapper reportMapper;
    private final ReportMongoService reportMongoService;

    @PostMapping("/migrate")
    public Map<String, Object> migrate() {
        log.info("[MIGRATE] h3_report MySQL → MongoDB 시작");

        List<Map<String, Object>> reports = reportMapper.selectAllReports();
        int total   = reports.size();
        int success = 0;
        int skip    = 0;

        for (Map<String, Object> row : reports) {
            try {
                long dailySeq = toLong(row.get("daily_seq"));

                // h3_report_ext → targets 배열로 임베딩
                List<Map<String, Object>> exts = reportMapper.selectReportExts(dailySeq);
                List<Map<String, Object>> targets = new ArrayList<>();
                for (Map<String, Object> ext : exts) {
                    Map<String, Object> t = new LinkedHashMap<>();
                    t.put("s_1", ext.get("s_1"));
                    t.put("s_2", ext.get("s_2"));
                    t.put("s_3", ext.get("s_3"));
                    targets.add(t);
                }

                // MongoDB 도큐먼트 구성
                Map<String, Object> doc = new LinkedHashMap<>(row);
                doc.put("targets", targets);

                reportMongoService.insert(doc);
                success++;

                if (success % 50 == 0) {
                    log.info("[MIGRATE] {} / {} 처리 중...", success, total);
                }
            } catch (Exception e) {
                log.error("[MIGRATE] daily_seq={} 실패: {}", row.get("daily_seq"), e.getMessage());
                skip++;
            }
        }

        log.info("[MIGRATE] 완료 — 전체:{}, 성공:{}, 실패:{}", total, success, skip);
        return Map.of(
            "result",  "success",
            "total",   total,
            "success", success,
            "failed",  skip
        );
    }

    private long toLong(Object obj) {
        if (obj == null) return 0L;
        try { return Long.parseLong(obj.toString()); } catch (Exception e) { return 0L; }
    }
}
