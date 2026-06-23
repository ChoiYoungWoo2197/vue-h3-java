package com.h3.h3_java.api.service.analysis;

import com.h3.h3_java.api.dto.AccountDto;
import com.h3.h3_java.raw.mongo.AccountMongoService;
import com.h3.h3_java.media.naver.NaverApiClient;
import com.h3.h3_java.raw.mongo.DashboardMongoService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
public class KeywordReReportService {

    private final AccountMongoService accountMongo;
    private final DashboardMongoService mongoService;

    public Map<String, Object> getKeywordReReport(String userId, String keywords, String dKeywords) {
        AccountDto acc = accountMongo.findAccountDtoByUserId(userId);
        if (acc == null) return fail("1009", "계정을 확인해 주세요.");

        String advkey = acc.getAccountNaverCustomer();
        if (advkey == null || advkey.isBlank()) return noData();

        // 기존 등록 키워드 조회 (MongoDB naver_keyword) + dKeywords 조합
        String checkKeywords = buildCheckKeywords(advkey, dKeywords);

        // Naver keywordstool API 호출
        NaverApiClient naverApi = new NaverApiClient(
            acc.getAccountNaverAccess(),
            acc.getAccountNaverSecret(),
            advkey
        );

        Map<String, Object> res = naverApi.get("/keywordstool", Map.of(
            "hintKeywords", keywords != null ? keywords : "",
            "showDetail",   "1"
        ));

        if (res == null) return noData();

        List<Map<String, Object>> data = new ArrayList<>();

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> keywordList = (List<Map<String, Object>>) res.get("keywordList");
        if (keywordList != null) {
            for (Map<String, Object> kw : keywordList) {
                String relKeyword = (String) kw.get("relKeyword");
                if (relKeyword == null) continue;

                // checkKeywords에 포함된 키워드 제외 (PHP preg_match 동일)
                if (!checkKeywords.isEmpty() && checkKeywords.contains(relKeyword)) continue;

                Map<String, Object> row = new LinkedHashMap<>();
                row.put("keyword_name",               relKeyword);
                row.put("monthly_pc_qc_cnt",           parseQcCnt(kw.get("monthlyPcQcCnt")));
                row.put("monthly_mobile_qc_cnt",       parseQcCnt(kw.get("monthlyMobileQcCnt")));
                row.put("monthly_ave_pc_clk_cnt",      kw.getOrDefault("monthlyAvePcClkCnt", 0));
                row.put("monthly_ave_mobile_clk_cnt",  kw.getOrDefault("monthlyAveMobileClkCnt", 0));
                row.put("monthly_ave_pc_ctr",          kw.getOrDefault("monthlyAvePcCtr", 0));
                row.put("monthly_ave_mobile_ctr",      kw.getOrDefault("monthlyAveMobileCtr", 0));
                row.put("pl_avg_depth",                kw.getOrDefault("plAvgDepth", 0));
                row.put("comp_idx",                    parseCompIdx(kw.get("compIdx")));
                data.add(row);
            }
        }

        if (data.isEmpty()) return noData();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("result", "success");
        result.put("status", "200");
        result.put("data", Map.of("keywords", data));
        return result;
    }

    // ─── 헬퍼 ────────────────────────────────────────────────────────────────

    private String buildCheckKeywords(String advkey, String dKeywords) {
        StringBuilder sb = new StringBuilder();
        if (dKeywords != null && !dKeywords.isBlank()) sb.append(dKeywords);

        List<String> existing = mongoService.findNaverKeywordNames(advkey);
        for (String kw : existing) {
            if (sb.length() > 0) sb.append(",");
            sb.append(kw);
        }
        return sb.toString();
    }

    private Object parseQcCnt(Object val) {
        if (val instanceof String s && s.startsWith("<")) return 0;
        return val != null ? val : 0;
    }

    private int parseCompIdx(Object val) {
        if (val instanceof String s) {
            if (s.contains("낮음")) return 0;
            if (s.contains("중간")) return 1;
            return 2;
        }
        return 0;
    }

    private Map<String, Object> fail(String status, String msg) {
        return Map.of("result", "failed", "status", status, "errormessage", msg);
    }

    private Map<String, Object> noData() {
        return Map.of("result", "success", "status", "1004", "errormessage", "검색 결과가 없습니다.");
    }
}
