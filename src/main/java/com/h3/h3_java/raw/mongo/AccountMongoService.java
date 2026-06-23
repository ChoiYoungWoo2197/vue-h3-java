package com.h3.h3_java.raw.mongo;

import com.h3.h3_java.api.dto.AccountDto;
import com.h3.h3_java.media.google.dto.GoogleAccountDto;
import com.h3.h3_java.media.kakao.dto.KakaoMoAccountDto;
import com.h3.h3_java.media.kakao.dto.KakaoSaAccountDto;
import com.h3.h3_java.media.naver.dto.NaverAccountDto;
import com.h3.h3_java.media.naver.dto.NaverGfaAccountDto;
import lombok.RequiredArgsConstructor;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AccountMongoService {

    private final MongoTemplate mongo;
    private static final String COL = "h3_account";

    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public void insert(Map<String, Object> data) {
        mongo.insert(new Document(data), COL);
    }

    public boolean existsByUserId(String userId) {
        return mongo.exists(Query.query(Criteria.where("user_id").is(userId)), COL);
    }

    public Document findByUserId(String userId) {
        return mongo.findOne(Query.query(Criteria.where("user_id").is(userId)), Document.class, COL);
    }

    public void upsertNaver(String userId, String naverid, String navercustomer,
                            String naveraccess, String naversecret) {
        Update update = new Update()
                .set("account_naver",          naverid)
                .set("account_naver_customer",  navercustomer)
                .set("account_naver_access",    naveraccess)
                .set("account_naver_secret",    naversecret)
                .set("account_date",            now())
                .setOnInsert("user_id",         userId)
                .setOnInsert("account_regdate", now());
        mongo.upsert(Query.query(Criteria.where("user_id").is(userId)), update, COL);
    }

    public void clearNaver(String userId) {
        Update update = new Update()
                .unset("account_naver").unset("account_naver_customer")
                .unset("account_naver_access").unset("account_naver_secret")
                .set("account_date", now());
        mongo.updateFirst(Query.query(Criteria.where("user_id").is(userId)), update, COL);
    }

    public void upsertKakaoSa(String userId, String kakaosaid) {
        Update update = new Update()
                .set("account_kakaosa", kakaosaid)
                .set("account_date",    now())
                .setOnInsert("user_id",         userId)
                .setOnInsert("account_regdate", now());
        mongo.upsert(Query.query(Criteria.where("user_id").is(userId)), update, COL);
    }

    public void clearKakaoSa(String userId) {
        mongo.updateFirst(Query.query(Criteria.where("user_id").is(userId)),
                new Update().unset("account_kakaosa").set("account_date", now()), COL);
    }

    public void upsertNaverDa(String userId, String naverdaid) {
        Update update = new Update()
                .set("account_gfa",  naverdaid)
                .set("account_date", now())
                .setOnInsert("user_id",         userId)
                .setOnInsert("account_regdate", now());
        mongo.upsert(Query.query(Criteria.where("user_id").is(userId)), update, COL);
    }

    public void clearNaverDa(String userId) {
        mongo.updateFirst(Query.query(Criteria.where("user_id").is(userId)),
                new Update().unset("account_gfa").set("account_date", now()), COL);
    }

    public void upsertKakaoMo(String userId, String kakaomomentid) {
        Update update = new Update()
                .set("account_kakaomoment", kakaomomentid)
                .set("account_date",        now())
                .setOnInsert("user_id",         userId)
                .setOnInsert("account_regdate", now());
        mongo.upsert(Query.query(Criteria.where("user_id").is(userId)), update, COL);
    }

    public void clearKakaoMo(String userId) {
        mongo.updateFirst(Query.query(Criteria.where("user_id").is(userId)),
                new Update().unset("account_kakaomoment").set("account_date", now()), COL);
    }

    public void upsertGoogle(String userId, String googleid) {
        Update update = new Update()
                .set("account_google", googleid)
                .set("account_date",   now())
                .setOnInsert("user_id",         userId)
                .setOnInsert("account_regdate", now());
        mongo.upsert(Query.query(Criteria.where("user_id").is(userId)), update, COL);
    }

    public void clearGoogle(String userId) {
        mongo.updateFirst(Query.query(Criteria.where("user_id").is(userId)),
                new Update().unset("account_google").set("account_date", now()), COL);
    }

    public List<Document> findAll() {
        return mongo.findAll(Document.class, COL);
    }

    // ── 매체별 계정 목록 조회 ─────────────────────────────────────────────────

    public List<Document> findNaverAccounts() {
        Query q = Query.query(Criteria.where("account_naver_access").exists(true)
            .and("account_naver_secret").exists(true)
            .and("account_naver_customer").exists(true));
        return mongo.find(q, Document.class, COL);
    }

    public List<Document> findGfaAccounts() {
        Query q = Query.query(Criteria.where("account_gfa").exists(true).ne(null).ne(""));
        return mongo.find(q, Document.class, COL);
    }

    public List<Document> findKakaoSaAccounts() {
        Query q = Query.query(Criteria.where("account_kakaosa").exists(true).ne(null).ne(""));
        return mongo.find(q, Document.class, COL);
    }

    public List<Document> findKakaoMoAccounts() {
        Query q = Query.query(Criteria.where("account_kakaomoment").exists(true).ne(null).ne(""));
        return mongo.find(q, Document.class, COL);
    }

    public List<Document> findGoogleAccounts() {
        Query q = Query.query(Criteria.where("account_google").exists(true).ne(null).ne(""));
        return mongo.find(q, Document.class, COL);
    }

    // ── DTO 반환 메서드 (배치 Job · 분석 서비스 전용) ───────────────────────────

    public List<NaverGfaAccountDto> findGfaAccountDtos() {
        return findGfaAccounts().stream().map(doc -> {
            NaverGfaAccountDto dto = new NaverGfaAccountDto();
            dto.setUserId(doc.getString("user_id"));
            dto.setAccountGfa(doc.getString("account_gfa"));
            return dto;
        }).collect(Collectors.toList());
    }

    public List<NaverAccountDto> findNaverAccountDtos() {
        return findNaverAccounts().stream().map(this::toNaverDto).collect(Collectors.toList());
    }

    public NaverAccountDto findNaverAccountDtoByUserId(String userId) {
        Document doc = findByUserId(userId);
        if (doc == null || doc.getString("account_naver_access") == null) return null;
        return toNaverDto(doc);
    }

    private NaverAccountDto toNaverDto(Document doc) {
        NaverAccountDto dto = new NaverAccountDto();
        dto.setUserId(doc.getString("user_id"));
        dto.setAccountNaverAccess(doc.getString("account_naver_access"));
        dto.setAccountNaverSecret(doc.getString("account_naver_secret"));
        dto.setAccountNaverCustomer(doc.getString("account_naver_customer"));
        return dto;
    }

    public List<KakaoSaAccountDto> findKakaoSaAccountDtos() {
        return findKakaoSaAccounts().stream().map(this::toKakaoSaDto).collect(Collectors.toList());
    }

    public KakaoSaAccountDto findKakaoSaAccountDtoByUserId(String userId) {
        Document doc = findByUserId(userId);
        if (doc == null || doc.getString("account_kakaosa") == null) return null;
        return toKakaoSaDto(doc);
    }

    private KakaoSaAccountDto toKakaoSaDto(Document doc) {
        KakaoSaAccountDto dto = new KakaoSaAccountDto();
        dto.setUserId(doc.getString("user_id"));
        dto.setAccountKakaosa(doc.getString("account_kakaosa"));
        return dto;
    }

    public List<KakaoMoAccountDto> findKakaoMoAccountDtos() {
        return findKakaoMoAccounts().stream().map(this::toKakaoMoDto).collect(Collectors.toList());
    }

    public KakaoMoAccountDto findKakaoMoAccountDtoByUserId(String userId) {
        Document doc = findByUserId(userId);
        if (doc == null || doc.getString("account_kakaomoment") == null) return null;
        return toKakaoMoDto(doc);
    }

    private KakaoMoAccountDto toKakaoMoDto(Document doc) {
        KakaoMoAccountDto dto = new KakaoMoAccountDto();
        dto.setUserId(doc.getString("user_id"));
        dto.setAccountKakaomoment(doc.getString("account_kakaomoment"));
        return dto;
    }

    public List<GoogleAccountDto> findGoogleAccountDtos() {
        return findGoogleAccounts().stream().map(this::toGoogleDto).collect(Collectors.toList());
    }

    public GoogleAccountDto findGoogleAccountDtoByUserId(String userId) {
        Document doc = findByUserId(userId);
        if (doc == null || doc.getString("account_google") == null) return null;
        return toGoogleDto(doc);
    }

    private GoogleAccountDto toGoogleDto(Document doc) {
        GoogleAccountDto dto = new GoogleAccountDto();
        dto.setUserId(doc.getString("user_id"));
        dto.setAccountGoogle(doc.getString("account_google"));
        return dto;
    }

    public String findUserIdByGfa(String advid) {
        Document doc = mongo.findOne(
            Query.query(Criteria.where("account_gfa").is(advid)), Document.class, COL);
        return doc != null ? doc.getString("user_id") : null;
    }

    public String findUserIdByKakaomoment(String advid) {
        Document doc = mongo.findOne(
            Query.query(Criteria.where("account_kakaomoment").is(advid)), Document.class, COL);
        return doc != null ? doc.getString("user_id") : null;
    }

    public String findUserIdByGoogle(String advid) {
        Document doc = mongo.findOne(
            Query.query(Criteria.where("account_google").is(advid)), Document.class, COL);
        return doc != null ? doc.getString("user_id") : null;
    }

    public AccountDto findAccountDtoByUserId(String userId) {
        Document doc = findByUserId(userId);
        if (doc == null) return null;
        AccountDto dto = new AccountDto();
        dto.setUserId(doc.getString("user_id"));
        dto.setAccountNaverAccess(doc.getString("account_naver_access"));
        dto.setAccountNaverSecret(doc.getString("account_naver_secret"));
        dto.setAccountNaverCustomer(doc.getString("account_naver_customer"));
        dto.setAccountKakaosa(doc.getString("account_kakaosa"));
        dto.setAccountKakaomoment(doc.getString("account_kakaomoment"));
        dto.setAccountGfa(doc.getString("account_gfa"));
        dto.setAccountGoogle(doc.getString("account_google"));
        return dto;
    }

    private String now() {
        return LocalDateTime.now().format(DT_FMT);
    }
}
