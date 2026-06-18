package com.h3.h3_java.api.service.dashboard;

import com.h3.h3_java.media.kakao.KakaoMoApiClient;
import com.h3.h3_java.media.kakao.KakaoSaApiClient;
import com.h3.h3_java.media.naver.NaverApiClient;
import com.h3.h3_java.raw.mongo.AccountMongoService;
import com.h3.h3_java.raw.mongo.KakaoMoTokenMongoService;
import com.h3.h3_java.raw.mongo.KakaoSaTokenMongoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class BalanceService {

    private final AccountMongoService      accountMongo;
    private final KakaoSaTokenMongoService kakaoSaTokenMongo;
    private final KakaoMoTokenMongoService kakaoMoTokenMongo;

    public Map<String, Object> getBalance(String userId) {
        Document account = accountMongo.findByUserId(userId);
        if (account == null) {
            return buildResponse(-1, -1, -1, -1, -1);
        }

        long naverBalance   = fetchNaverSaBalance(account);
        long kakaoSaBalance = fetchKakaoSaBalance(account);
        long kakaoMoBalance = fetchKakaoMoBalance(account);
        long naverdaBalance = isBlank(account.getString("account_gfa"))       ? -1 : 0;
        long googleBalance  = isBlank(account.getString("account_google"))    ? -1 : 0;

        return buildResponse(naverBalance, kakaoSaBalance, kakaoMoBalance, naverdaBalance, googleBalance);
    }

    // -------------------------------------------------------------------------

    private long fetchNaverSaBalance(Document account) {
        String access   = account.getString("account_naver_access");
        String secret   = account.getString("account_naver_secret");
        String customer = account.getString("account_naver_customer");

        if (isBlank(access) || isBlank(secret) || isBlank(customer)) return -1;

        try {
            NaverApiClient client = new NaverApiClient(access, secret, customer);
            Map<String, Object> res = client.get("/billing/bizmoney");
            if (res == null || !res.containsKey("bizmoney")) return 0;
            Number bizmoney = (Number) res.get("bizmoney");
            return bizmoney != null ? bizmoney.longValue() : 0;
        } catch (Exception e) {
            log.warn("[Balance] Naver SA bizmoney 조회 실패: {}", e.getMessage());
            return 0;
        }
    }

    private long fetchKakaoSaBalance(Document account) {
        String adAccountId = account.getString("account_kakaosa");
        if (isBlank(adAccountId)) return -1;

        try {
            Document tokenDoc = kakaoSaTokenMongo.findToken();
            if (tokenDoc == null) return 0;
            String token = tokenDoc.getString("access_token");
            if (isBlank(token)) return 0;

            KakaoSaApiClient client = new KakaoSaApiClient(token, adAccountId);
            Map<String, Object> res = client.get("/openapi/v1/adAccounts/balance", Collections.emptyMap());
            if (res == null || !res.containsKey("cash")) return 0;
            Number cash = (Number) res.get("cash");
            return cash != null ? cash.longValue() : 0;
        } catch (Exception e) {
            log.warn("[Balance] Kakao SA balance 조회 실패: {}", e.getMessage());
            return 0;
        }
    }

    private long fetchKakaoMoBalance(Document account) {
        String adAccountId = account.getString("account_kakaomoment");
        if (isBlank(adAccountId)) return -1;

        try {
            Document tokenDoc = kakaoMoTokenMongo.findToken();
            if (tokenDoc == null) return 0;
            String token = tokenDoc.getString("access_token");
            if (isBlank(token)) return 0;

            KakaoMoApiClient client = new KakaoMoApiClient(token, adAccountId);
            Map<String, Object> res = client.get("/openapi/v4/adAccounts/balance", Collections.emptyMap());
            if (res == null || !res.containsKey("cash")) return 0;
            Number cash = (Number) res.get("cash");
            return cash != null ? cash.longValue() : 0;
        } catch (Exception e) {
            log.warn("[Balance] Kakao MO balance 조회 실패: {}", e.getMessage());
            return 0;
        }
    }

    // -------------------------------------------------------------------------

    private Map<String, Object> buildResponse(long naver, long kakaosa, long kakaomo, long naverda, long google) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("naver",   Map.of("balance", naver));
        data.put("kakaosa", Map.of("balance", kakaosa));
        data.put("kakaomo", Map.of("balance", kakaomo));
        data.put("naverda", Map.of("balance", naverda));
        data.put("google",  Map.of("balance", google));

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("result", "success");
        res.put("status", "200");
        res.put("data",   data);
        return res;
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
