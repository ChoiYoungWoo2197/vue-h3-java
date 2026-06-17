package com.h3.h3_java.api.service.user;

import com.h3.h3_java.api.mapper.AccountMapper;
import com.h3.h3_java.media.naver.NaverApiClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccountService {

    private final AccountMapper accountMapper;

    // =========================================================================
    // L — 계정 조회
    // =========================================================================

    public Map<String, Object> getAccount(String userId, String media) {
        Map<String, Object> full = accountMapper.selectFullByUserId(userId);

        Map<String, Object> account = new LinkedHashMap<>();
        if (full == null) {
            account = emptyAccount(media);
        } else {
            switch (media) {
                case "naver":
                    account.put("naverid",      full.get("naverid"));
                    account.put("navercustomer", full.get("navercustomer"));
                    account.put("naveraccess",   full.get("naveraccess"));
                    account.put("naversecret",   full.get("naversecret"));
                    break;
                case "kakaosa":
                    account.put("kakaosaid", full.get("kakaosaid"));
                    break;
                case "naverda":
                    account.put("naverdaid", full.get("naverdaid"));
                    break;
                case "kakaomo":
                    account.put("kakaomomentid", full.get("kakaomomentid"));
                    break;
                case "google":
                    account.put("googleid", full.get("googleid"));
                    break;
                default:
                    return fail("400", "지원하지 않는 media입니다.");
            }
        }

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("result", "success");
        res.put("status", "200");
        res.put("account", account);
        return res;
    }

    // =========================================================================
    // I/U — 계정 저장 (upsert)
    // =========================================================================

    public Map<String, Object> saveAccount(String userId, String media,
                                           String naverid, String navercustomer,
                                           String naveraccess, String naversecret,
                                           String kakaosaid, String naverdaid,
                                           String kakaomomentid, String googleid) {
        try {
            switch (media) {
                case "naver":
                    accountMapper.upsertNaver(userId, naverid, navercustomer, naveraccess, naversecret);
                    break;
                case "kakaosa":
                    accountMapper.upsertKakaoSa(userId, kakaosaid);
                    break;
                case "naverda":
                    accountMapper.upsertNaverDa(userId, naverdaid);
                    break;
                case "kakaomo":
                    accountMapper.upsertKakaoMo(userId, kakaomomentid);
                    break;
                case "google":
                    accountMapper.upsertGoogle(userId, googleid);
                    break;
                default:
                    return fail("400", "지원하지 않는 media입니다.");
            }
            return ok();
        } catch (Exception e) {
            log.error("[AccountService] saveAccount 오류 userId={} media={} error={}", userId, media, e.getMessage(), e);
            return fail("500", "저장 중 오류가 발생했습니다.");
        }
    }

    // =========================================================================
    // D — 계정 삭제 (해당 매체 필드 null)
    // =========================================================================

    public Map<String, Object> deleteAccount(String userId, String media) {
        try {
            switch (media) {
                case "naver":   accountMapper.clearNaver(userId);   break;
                case "kakaosa": accountMapper.clearKakaoSa(userId); break;
                case "naverda": accountMapper.clearNaverDa(userId); break;
                case "kakaomo": accountMapper.clearKakaoMo(userId); break;
                case "google":  accountMapper.clearGoogle(userId);  break;
                default:
                    return fail("400", "지원하지 않는 media입니다.");
            }
            return ok();
        } catch (Exception e) {
            log.error("[AccountService] deleteAccount 오류 userId={} media={} error={}", userId, media, e.getMessage(), e);
            return fail("500", "삭제 중 오류가 발생했습니다.");
        }
    }

    // =========================================================================
    // 계정 검증 (저장 전 API 유효성 확인)
    // =========================================================================

    public Map<String, Object> validateAccount(String media,
                                               String navercustomer, String naveraccess, String naversecret,
                                               String kakaosaid, String naverdaid,
                                               String kakaomomentid, String googleid) {
        switch (media) {
            case "naver":
                return validateNaver(navercustomer, naveraccess, naversecret);
            case "kakaosa":
                return validateAcNo("kakaosa", kakaosaid);
            case "naverda":
                return validateAcNo("naverda", naverdaid);
            case "kakaomo":
                return validateAcNo("kakaomo", kakaomomentid);
            case "google":
                return validateAcNo("google", googleid);
            default:
                return fail("400", "지원하지 않는 media입니다.");
        }
    }

    // ─── 네이버 SA: 실제 API 호출로 검증 ─────────────────────────────────────

    private Map<String, Object> validateNaver(String navercustomer, String naveraccess, String naversecret) {
        if (isBlank(navercustomer) || isBlank(naveraccess) || isBlank(naversecret)) {
            return validateFail("필수값(navercustomer, naveraccess, naversecret)이 없습니다.", "naver");
        }
        try {
            NaverApiClient client = new NaverApiClient(naveraccess, naversecret, navercustomer);
            Map<String, Object> res = client.get("/billing/bizmoney");

            if (res == null || res.isEmpty()) {
                return validateFail("네이버 API 응답이 비어있습니다.", "naver");
            }
            Object status = res.get("status");
            if (status != null && Integer.parseInt(String.valueOf(status)) >= 400) {
                String detail = res.containsKey("detail") ? String.valueOf(res.get("detail")) : "네이버 API 인증에 실패했습니다.";
                return validateFail(detail, "naver");
            }
            if (res.containsKey("customerId")) {
                return validateOk("네이버 API 연동 확인 성공", "naver");
            }
            return validateFail("네이버 API 응답이 정상 형식이 아닙니다.", "naver");
        } catch (Exception e) {
            log.warn("[AccountService] 네이버 validate 오류: {}", e.getMessage());
            return validateFail("네이버 API 호출 오류: " + e.getMessage(), "naver");
        }
    }

    // ─── 카카오/GFA/구글: 계정번호 형식 검증 ─────────────────────────────────

    private Map<String, Object> validateAcNo(String media, String acNo) {
        if (isBlank(acNo)) {
            return validateFail("계정번호(acNo)가 없습니다.", media);
        }
        if (!acNo.matches("\\d+")) {
            return validateFail("계정번호는 숫자여야 합니다.", media);
        }
        return validateOk("계정번호 형식 확인 완료", media);
    }

    // =========================================================================
    // 유틸리티
    // =========================================================================

    private Map<String, Object> ok() {
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("result", "success");
        res.put("status", "200");
        return res;
    }

    private Map<String, Object> fail(String status, String message) {
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("result", "failed");
        res.put("status", status);
        res.put("errormessage", message);
        return res;
    }

    private Map<String, Object> validateOk(String message, String media) {
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("result", "success");
        res.put("status", "200");
        res.put("data", Map.of("message", message, "media", media));
        return res;
    }

    private Map<String, Object> validateFail(String message, String media) {
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("result", "fail");
        res.put("status", "400");
        res.put("data", Map.of("message", message, "media", media));
        return res;
    }

    private Map<String, Object> emptyAccount(String media) {
        Map<String, Object> m = new LinkedHashMap<>();
        switch (media) {
            case "naver":
                m.put("naverid", null); m.put("navercustomer", null);
                m.put("naveraccess", null); m.put("naversecret", null);
                break;
            case "kakaosa":  m.put("kakaosaid", null);      break;
            case "naverda":  m.put("naverdaid", null);       break;
            case "kakaomo":  m.put("kakaomomentid", null);   break;
            case "google":   m.put("googleid", null);        break;
        }
        return m;
    }

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
