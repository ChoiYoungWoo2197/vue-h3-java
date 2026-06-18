package com.h3.h3_java.api.collector;

import com.h3.h3_java.api.mapper.AccountLogMapper;
import com.h3.h3_java.raw.mongo.AccountLogMongoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/collector")
@RequiredArgsConstructor
public class AccountLogMigrateController {

    private final AccountLogMapper      accountLogMapper;
    private final AccountLogMongoService accountLogMongo;

    @PostMapping("/account-log-migrate")
    public Map<String, Object> migrate() {
        List<Map<String, Object>> rows = accountLogMapper.selectAll();
        int success = 0, failed = 0;

        for (Map<String, Object> row : rows) {
            try {
                Document doc = new Document(row);
                accountLogMongo.upsert(doc);
                success++;
            } catch (Exception e) {
                log.error("[AccountLogMigrate] advkey={} 실패", row.get("advkey"), e);
                failed++;
            }
        }

        log.info("[AccountLogMigrate] 완료 total={} success={} failed={}", rows.size(), success, failed);
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("result",  "success");
        res.put("total",   rows.size());
        res.put("success", success);
        res.put("failed",  failed);
        return res;
    }
}
