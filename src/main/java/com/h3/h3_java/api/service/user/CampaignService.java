package com.h3.h3_java.api.service.user;

import com.h3.h3_java.media.naver.NaverApiClient;
import com.h3.h3_java.raw.mongo.AccountMongoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CampaignService {

    private final AccountMongoService accountMongo;
    private final MongoTemplate       mongo;

    public Map<String, Object> getCampaigns(String userId, String media) {
        Document account = accountMongo.findByUserId(userId);
        if (account == null) return fail("계정 정보가 없습니다.");

        try {
            List<Map<String, Object>> campaigns;
            switch (media) {
                case "naver_sa":    campaigns = fetchNaverSa(account);   break;
                case "naver_da":    campaigns = fetchNaverGfa(account);  break;
                case "kakao_sa":    campaigns = fetchMongo(account.getString("account_kakaosa"),    "kakao_sa_campaign");  break;
                case "kakao_da":    campaigns = fetchMongo(account.getString("account_kakaomoment"), "kakao_mo_campaign"); break;
                case "google_ads":  campaigns = fetchMongo(account.getString("account_google"),     "google_campaign");   break;
                default:            return fail("지원하지 않는 media: " + media);
            }
            return success(campaigns);
        } catch (Exception e) {
            log.error("[Campaign] getCampaigns userId={} media={} error={}", userId, media, e.getMessage());
            return fail("캠페인 조회 중 오류가 발생했습니다.");
        }
    }

    // -------------------------------------------------------------------------
    // Naver SA — 실시간 API 호출
    // -------------------------------------------------------------------------

    private List<Map<String, Object>> fetchNaverSa(Document account) {
        String access   = account.getString("account_naver_access");
        String secret   = account.getString("account_naver_secret");
        String customer = account.getString("account_naver_customer");

        if (isBlank(access) || isBlank(secret) || isBlank(customer)) return Collections.emptyList();

        NaverApiClient client = new NaverApiClient(access, secret, customer);
        List<Map<String, Object>> raw = client.getList("/ncc/campaigns", Map.of("recordSize", "100"));
        if (raw == null) return Collections.emptyList();

        return raw.stream().map(c -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("campaignId",   c.get("nccCampaignId"));
            m.put("campaignName", c.get("name"));
            m.put("status",       mapNaverSaStatus((String) c.get("status")));
            return m;
        }).collect(Collectors.toList());
    }

    private String mapNaverSaStatus(String status) {
        if ("ELIGIBLE".equals(status)) return "운영중";
        if ("PAUSED".equals(status))   return "일시중지";
        if ("DELETED".equals(status))  return "종료";
        return status != null ? status : "";
    }

    // -------------------------------------------------------------------------
    // Naver GFA — MongoDB naver_gfa_campaign
    // -------------------------------------------------------------------------

    private List<Map<String, Object>> fetchNaverGfa(Document account) {
        return fetchMongo(account.getString("account_gfa"), "naver_gfa_campaign");
    }

    // -------------------------------------------------------------------------
    // 공통 MongoDB 캠페인 조회 (GFA / Kakao SA / Kakao MO / Google)
    // advkey + (cid, cname, onoff) 구조 공통
    // -------------------------------------------------------------------------

    private List<Map<String, Object>> fetchMongo(String advkey, String collection) {
        if (isBlank(advkey)) return Collections.emptyList();

        Query q = Query.query(Criteria.where("advkey").is(advkey));
        return mongo.find(q, Document.class, collection).stream()
                .map(d -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("campaignId",   d.getString("cid"));
                    m.put("campaignName", d.getString("cname"));
                    m.put("status",       d.getInteger("onoff", 1) == 1 ? "운영중" : "일시중지");
                    return m;
                })
                .collect(Collectors.toList());
    }

    // -------------------------------------------------------------------------
    // 응답 헬퍼
    // -------------------------------------------------------------------------

    private Map<String, Object> success(List<Map<String, Object>> campaigns) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("campaigns", campaigns);

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("result", "success");
        res.put("status", "200");
        res.put("data",   data);
        return res;
    }

    private Map<String, Object> fail(String message) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("message", message);

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("result", "fail");
        res.put("status", "400");
        res.put("data",   data);
        return res;
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
