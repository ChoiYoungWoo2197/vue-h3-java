# h3-java

PHP 크론 스크립트(`h3-백엔드`)를 Spring Boot로 이식한 광고 데이터 수집 서비스.  
네이버·카카오·구글 광고 플랫폼 데이터를 수집해 MySQL과 MongoDB에 저장한다.

---

## 기술 스택

| 분류 | 기술 |
|---|---|
| Language | Java 21 (Virtual Thread 사용) |
| Framework | Spring Boot 3.3.5 |
| ORM | MyBatis 3.0.3 (XML mapper) |
| Batch | Spring Batch |
| Message Queue | RabbitMQ (Spring AMQP) |
| DB | MySQL (통계·마스터), MongoDB Atlas (raw 마스터·시간별·delta) |
| Build | Gradle |
| Etc | Lombok |

---

## 아키텍처

시스템은 두 개의 독립적인 흐름으로 구성된다.

### 1. 수집 흐름 (Collection)

```
[ Collector REST API ]   [ CollectorScheduler (cron) ]
          │                          │
          └──────────┬───────────────┘
                     ▼
           RabbitMQ (h3.collector DirectExchange)
                     │
                     ▼
             CollectorConsumer
                     │
              ┌──────┴──────┐
              │             │
          Master Job     Stat Job
              │             │
              ▼             ▼
           MongoDB     MySQL + MongoDB
```

- 수집 트리거: Collector REST API(`/api/collector/**`) 또는 `CollectorScheduler`(cron)
- 단일 유저·기간 요청은 항상 **MQ 경유 비동기** 처리
- Consumer에서 `hasRange()` 분기 → `collectRange` 또는 `collectForUserId`

### 2. 조회 흐름 (Service Layer)

```
[ vue-h3 Frontend ]
        │  Authorization: Bearer <JWT>
        ▼
  Spring Security (JwtFilter)
        │
        ▼
  Service Layer API
  (/v1/h3/app/dashboard/**, /v1/h3/app/analysis/**)
        │                        │
        ▼                        ▼
     MongoDB                  OpenAI API
  (통계·마스터 조회)          (aiinsight/followup)
        │
        ▼
     MySQL
  (계정·마스터 조회)
```

- `/v1/h3/app/**` 경로 전체 JWT 인증 필수
- Dashboard: `summary`, `summarymedia`, `period`, `aiinsight`, `aiinsight_followup`
- Analysis: `campaign/adgroup/keyword/ad/media/periodreport`, `keywordrereport`, `shopping` 계열

---

## 패키지 구조

```
com.h3.h3_java
├── api/
│   ├── collector/      수집 트리거 REST 엔드포인트
│   ├── controller/     서비스 레이어 REST 엔드포인트 (대시보드·분석·리포트)
│   ├── service/
│   │   ├── dashboard/  DashboardService, AiInsightService
│   │   └── analysis/   CampaignReportService, AdgroupReportService,
│   │                   KeywordReportService, AdReportService, MediaReportService,
│   │                   KeywordReReportService, PeriodReportService,
│   │                   ShoppingReportService, AdgroupShoppingReportService,
│   │                   CampaignShoppingReportService
│   ├── dto/            계정 DTO
│   └── mapper/         계정 MyBatis mapper
├── auth/               JWT 인증·인가 (JwtUtil, JwtFilter, SecurityConfig)
├── batch/
│   ├── master/         마스터 수집 Job (캠페인·광고그룹·소재·키워드 구조)
│   ├── stat/           일별·시간별·TSV 통계 수집 Job
│   ├── aggregation/    집계 처리 (준비 중)
│   └── scheduler/      정기 스케줄 발행 + 신규 계정 자동 감지
├── queue/
│   ├── message/        MQ 메시지 DTO
│   ├── producer/       MQ 발행
│   └── consumer/       MQ 소비 → Job 라우팅
├── media/naver/
│   ├── (root)          API 클라이언트, TSV 파서
│   ├── dto/            네이버 API 응답 DTO
│   └── mapper/         MyBatis mapper 인터페이스
├── raw/mongo/          MongoDB raw 저장 서비스 (마스터·통계·delta)
├── common/
│   ├── config/         RabbitMQ 등 공통 설정
│   ├── constants/      공통 상수 (준비 중)
│   ├── exception/      공통 예외 처리 (준비 중)
│   └── util/           공통 유틸리티 (준비 중)
└── config/             스프링 전역 설정 (준비 중)
```

---

## 스케줄 (Asia/Seoul)

| 시각 | Job |
|---|---|
| 01:00 | 네이버 GFA 마스터 |
| 02:00 | 네이버 SA 마스터 |
| 03:00 | 네이버 SA 캠페인 일별 |
| 03:30 | 네이버 SA 캠페인 시간별 |
| 04:00 | 네이버 SA 광고그룹 일별 |
| 04:30 | 네이버 SA 소재 일별 |
| 04:45 | 네이버 SA 쇼핑소재 일별 |
| 05:00 | 네이버 SA StateReport (키워드·타겟) |
| 05:30 | 네이버 SA 전환유형 |
| 06:00 | 네이버 GFA 캠페인 일별 |
| 06:30 | 네이버 GFA 광고그룹 일별 |
| 07:00 | 네이버 GFA 예산알람 |
| 07:30 | 네이버 GFA 소재 일별 |
| 08:00 | 네이버 GFA 전환유형 |
| 09:00 | 카카오 SA 마스터 |
| 10:00 | 카카오 SA 캠페인 일별 |
| 10:30 | 카카오 SA 캠페인 시간별 |
| 11:00 | 카카오 SA 광고그룹 일별 |
| 11:30 | 카카오 SA 키워드 일별 |
| 12:00 | 카카오 SA 소재 일별 |
| 12:30 | 카카오 SA 예산알람 |
| 13:00 | 카카오 MO 마스터 |
| 14:00 | 카카오 MO 캠페인 일별 |
| 14:30 | 카카오 MO 캠페인 시간별 |
| 15:00 | 카카오 MO 광고그룹 일별 |
| 15:30 | 카카오 MO 소재 일별 |
| 16:00 | 카카오 MO 예산알람 |
| 17:00 | 구글 마스터 |
| 18:00 | 구글 캠페인 일별 |
| 18:30 | 구글 캠페인 시간별 |
| 19:00 | 구글 광고그룹 일별 |
| 19:30 | 구글 소재 일별 |
| 20:00 | 구글 키워드 일별 |
| 매 10분 | 신규 계정 감지 (NaverNewAccountScheduler) |

---

## 인증 (JWT)

`/v1/h3/app/**` 경로는 Bearer JWT 인증이 필요하다.

```
# 로그인
POST /v1/h3/auth/login
Body: { "userid": "...", "password": "..." }
Response: { "token": "<JWT>" }

# 이후 모든 /app/** 요청에 헤더 첨부
Authorization: Bearer <JWT>
```

- 토큰 유효기간: 24시간
- JWT secret: `application.yml` `jwt.secret` 참조
- `JwtFilter` → `SecurityConfig` → Spring Security 필터 체인

---

## 서비스 레이어 API (PHP → Java 이식 현황)

PHP `api/rest/app/` 서비스를 Java Spring Boot + MongoDB로 이식. JWT 인증 필요.

```
GET /v1/h3/app/dashboard/{endpoint}     # 대시보드
GET /v1/h3/app/analysis/{endpoint}      # 광고 분석
POST /v1/h3/app/dashboard/aiinsight          # AI 인사이트 분석
POST /v1/h3/app/dashboard/aiinsight_followup # AI 인사이트 추가 질문
```

| 구분 | 엔드포인트 | 상태 |
|---|---|---|
| Dashboard | `summarymedia` | ✅ 완료 |
| Dashboard | `summary` | ✅ 완료 |
| Dashboard | `period` | ✅ 완료 |
| Dashboard | `aiinsight` | ✅ 완료 (OpenAI gpt-4.1-mini) |
| Dashboard | `aiinsight_followup` | ✅ 완료 (OpenAI gpt-4.1-mini) |
| Analysis | `campaignreport` | ✅ 완료 |
| Analysis | `adgroupreport` | ✅ 완료 |
| Analysis | `keywordreport` | ✅ 완료 |
| Analysis | `adreport` | ✅ 완료 |
| Analysis | `mediareport` | ✅ 완료 |
| Analysis | `keywordrereport` | ✅ 완료 |
| Analysis | `periodreport` | ✅ 완료 |
| Shopping | `shoppingreport` | ✅ 완료 |
| Shopping | `adgroupshoppingreport` | ✅ 완료 |
| Shopping | `campaignshoppingreport` | ✅ 완료 |
| Analysis | `targetreport` | ⏳ 대기 (MySQL 전용) |
| Analysis | `campaignadreport`, `campaignkeywordreport` | ⏳ 대기 |
| Analysis | `adgroupadreport`, `adgroupkeywordreport` | ⏳ 대기 |

> ✅ 완료 15개 / ⏳ 대기 3개 (MySQL 전용, MongoDB 매핑 필요)

---

## REST API (수집)

```
# 네이버 SA
POST /api/collector/naver/{job-type}                                          # 전체 계정, 자동 날짜
POST /api/collector/naver/{job-type}/range?from=YYYY-MM-DD&to=YYYY-MM-DD     # 전체 계정, 기간 지정
POST /api/collector/naver/{job-type}/{userId}                                 # 단일 계정, 자동 날짜
POST /api/collector/naver/{job-type}/{userId}/range?from=YYYY-MM-DD&to=YYYY-MM-DD  # 단일 계정, 기간 지정
```
job-type (전체): `master`, `campaign-daily`, `campaign-hour`, `adgroup-daily`, `ad-daily`, `shopping-daily`, `state-report`, `conv-type`  
`/range` (전체 계정 기간): `campaign-daily`, `campaign-hour`, `adgroup-daily`, `ad-daily`, `shopping-daily`, `state-report`, `conv-type`

```
# 네이버 GFA
POST /api/collector/naver/gfa-{job-type}
POST /api/collector/naver/gfa-{job-type}/{userId}
POST /api/collector/naver/gfa-{job-type}/{userId}/range?from=YYYY-MM-DD&to=YYYY-MM-DD
```
job-type: `master`, `campaign-daily`, `adgroup-daily`, `ad-daily`, `budget-alarm`, `conv-type`

```
# 카카오 SA
POST /api/collector/kakao/sa/{job-type}
POST /api/collector/kakao/sa/{job-type}/{userId}
POST /api/collector/kakao/sa/{job-type}/{userId}/range?from=YYYY-MM-DD&to=YYYY-MM-DD
```
job-type: `master`, `campaign-daily`, `campaign-hour`, `adgroup-daily`, `ad-daily`, `keyword-daily`, `budget-alarm`

```
# 카카오 MO
POST /api/collector/kakao/mo/{job-type}
POST /api/collector/kakao/mo/{job-type}/{userId}
POST /api/collector/kakao/mo/{job-type}/{userId}/range?from=YYYY-MM-DD&to=YYYY-MM-DD
```
job-type: `master`, `campaign-daily`, `campaign-hour`, `adgroup-daily`, `ad-daily`, `budget-alarm`

```
# 구글
POST /api/collector/google/{job-type}
POST /api/collector/google/{job-type}/{userId}
POST /api/collector/google/{job-type}/{userId}/range?from=YYYY-MM-DD&to=YYYY-MM-DD
```
job-type: `master`, `campaign-daily`, `campaign-hour`, `adgroup-daily`, `ad-daily`, `keyword-daily`

---

## 데이터 저장소

### MySQL (대시보드·분석용)

| 테이블 | 설명 |
|---|---|
| `h3_account` | 매체 API 계정 정보 |
| `h3_naver_campaign` | 캠페인 마스터 |
| `h3_naver_adgroup` | 광고그룹 마스터 |
| `h3_naver_ad` | 소재 마스터 |
| `h3_naver_keyword` | 키워드 마스터 |
| `h3_naver_shoppingproduct` | 쇼핑소재 마스터 |
| `h3_campaign_daily_naver` | 캠페인 일별 통계 |
| `h3_adgroup_daily_naver` | 광고그룹 일별 통계 |
| `h3_ad_daily_naver` | 소재 일별 통계 |
| `h3_shopping_ad_daily_naver` | 쇼핑소재 일별 통계 |
| `h3_keyword_daily_naver_new` | 키워드 일별 통계 |
| `h3_target_daily_naver` | 타겟 일별 통계 |
| `h3_campaign_daily_naver_convtype` | 캠페인 전환유형 |
| `h3_adgroup_daily_naver_convtype` | 광고그룹 전환유형 |
| `h3_keyword_daily_naver_convtype` | 키워드 전환유형 |
| `h3_ad_daily_naver_convtype` | 소재 전환유형 |
| `h3_account_log` | 계정별 최종 수집일 |

### MongoDB (raw 마스터·시간별·delta)

| 컬렉션 | 설명 |
|---|---|
| `naver_campaign` | 캠페인 raw 마스터 |
| `naver_campaign_budget` | 캠페인 예산 |
| `naver_adgroup` | 광고그룹 raw 마스터 |
| `naver_adgroup_budget` | 광고그룹 예산 |
| `naver_ad` | 소재 raw 마스터 |
| `naver_keyword` | 키워드 raw 마스터 |
| `naver_adextension` | 광고 확장소재 |
| `naver_shopping_product` | 쇼핑소재 raw 마스터 |
| `naver_master_delta` | 마스터 수집 delta 추적 (증분 수집용) |
| `naver_campaign_hour` | 캠페인 시간별 통계 |
| `naver_campaign_daily` | 캠페인 일별 통계 |
| `naver_adgroup_daily` | 광고그룹 일별 통계 |
| `naver_ad_daily` | 소재 일별 통계 |
| `naver_shopping_ad_daily` | 쇼핑소재 일별 통계 |
| `naver_keyword_daily` | 키워드 일별 통계 |
| `naver_target_daily` | 타겟 일별 통계 |
| `naver_convtype_campaign` | 캠페인 전환유형 |
| `naver_convtype_adgroup` | 광고그룹 전환유형 |
| `naver_convtype_keyword` | 키워드 전환유형 |
| `naver_convtype_ad` | 소재 전환유형 |
| `naver_gfa_token` | 네이버 GFA OAuth 토큰 |
| `naver_gfa_campaign` | GFA 캠페인 마스터 |
| `naver_gfa_adgroup` | GFA 광고그룹 마스터 |
| `naver_gfa_ad` | GFA 소재 마스터 |
| `naver_gfa_campaign_daily` | GFA 캠페인 일별 통계 |
| `naver_gfa_adgroup_daily` | GFA 광고그룹 일별 통계 |
| `naver_gfa_ad_daily` | GFA 소재 일별 통계 |
| `kakao_sa_token` | 카카오 SA OAuth 토큰 |
| `kakao_sa_campaign` | 카카오 SA 캠페인 마스터 |
| `kakao_sa_adgroup` | 카카오 SA 광고그룹 마스터 |
| `kakao_sa_ad` | 카카오 SA 소재 마스터 |
| `kakao_sa_keyword` | 카카오 SA 키워드 마스터 |
| `kakao_sa_campaign_daily` | 카카오 SA 캠페인 일별 통계 |
| `kakao_sa_campaign_hour` | 카카오 SA 캠페인 시간별 통계 |
| `kakao_sa_adgroup_daily` | 카카오 SA 광고그룹 일별 통계 |
| `kakao_sa_ad_daily` | 카카오 SA 소재 일별 통계 |
| `kakao_sa_keyword_daily` | 카카오 SA 키워드 일별 통계 |
| `kakao_sa_budget_alarm` | 카카오 SA 예산알람 |
| `kakao_mo_token` | 카카오 MO OAuth 토큰 |
| `kakao_mo_campaign` | 카카오 MO 캠페인 마스터 |
| `kakao_mo_adgroup` | 카카오 MO 광고그룹 마스터 |
| `kakao_mo_ad` | 카카오 MO 소재 마스터 |
| `kakao_mo_campaign_daily` | 카카오 MO 캠페인 일별 통계 |
| `kakao_mo_campaign_hour` | 카카오 MO 캠페인 시간별 통계 |
| `kakao_mo_adgroup_daily` | 카카오 MO 광고그룹 일별 통계 |
| `kakao_mo_ad_daily` | 카카오 MO 소재 일별 통계 |
| `kakao_mo_budget_alarm` | 카카오 MO 예산알람 |
| `google_token` | 구글 OAuth 토큰 |
| `google_campaign` | 구글 캠페인 마스터 |
| `google_adgroup` | 구글 광고그룹 마스터 |
| `google_keyword` | 구글 키워드 마스터 |
| `google_ad` | 구글 소재 마스터 |
| `google_campaign_daily` | 구글 캠페인 일별 통계 |
| `google_campaign_hour` | 구글 캠페인 시간별 통계 |
| `google_adgroup_daily` | 구글 광고그룹 일별 통계 |
| `google_ad_daily` | 구글 소재 일별 통계 |
| `google_keyword_daily` | 구글 키워드 일별 통계 |

---

## OAuth 토큰 관리

광고 플랫폼 OAuth 토큰(Access Token / Refresh Token)을 Java로 발급·갱신한다.  
`/v1/h3/token/**` 경로는 `permitAll` — JWT 인증 없이 브라우저에서 직접 호출 가능.

### 공통 흐름

1. 브라우저에서 `oauth` URL 접속 → 플랫폼 인가 화면
2. 동의 완료 → `callback` URL로 자동 redirect → MongoDB에 토큰 저장
3. 이후 앱 자동갱신 스케줄러가 30분 주기로 갱신

### 네이버 GFA

| 구분 | URL |
|---|---|
| 최초 발급 | `GET https://api.heeil.com/java/v1/h3/token/naver-gfa/oauth` |
| 콜백 (자동) | `GET https://api.heeil.com/java/v1/h3/token/naver-gfa/callback?code=&state=` |
| 수동 갱신 | `POST https://api.heeil.com/java/v1/h3/token/naver-gfa/refresh` |

- client_id/secret: MongoDB `h3_account` admin 계정 `account_gfa` / `account_naver_secret` 필드
- 저장 컬렉션: `naver_gfa_token` (key: `"navergfa"`)

### 카카오 SA

| 구분 | URL |
|---|---|
| 최초 발급 | `GET https://api.heeil.com/java/v1/h3/token/kakao-sa/oauth` |
| 콜백 (자동) | `GET https://api.heeil.com/java/v1/h3/token/kakao-sa/callback?code=` |
| 수동 갱신 | `POST https://api.heeil.com/java/v1/h3/token/kakao-sa/refresh` |

- client_id: `application.yml` `kakao.sa.client-id`
- `&prompt=none` 포함 (자동 재인가)
- 저장 컬렉션: `kakao_sa_token` (key: `"kakaosa"`)

### 카카오 MO

| 구분 | URL |
|---|---|
| 최초 발급 | `GET https://api.heeil.com/java/v1/h3/token/kakao-mo/oauth` |
| 콜백 (자동) | `GET https://api.heeil.com/java/v1/h3/token/kakao-mo/callback?code=` |
| 수동 갱신 | `POST https://api.heeil.com/java/v1/h3/token/kakao-mo/refresh` |

- client_id: `application.yml` `kakao.mo.client-id` (SA와 **다른** 앱)
- `&prompt=none` 없음
- 저장 컬렉션: `kakao_mo_token` (key: `"kakaomo"`)

### 구글

| 구분 | URL |
|---|---|
| 최초 발급 | `GET https://api.heeil.com/java/v1/h3/token/google/oauth` |
| 콜백 (자동) | `GET https://api.heeil.com/java/v1/h3/token/google/callback?code=` |
| 수동 갱신 | `POST https://api.heeil.com/java/v1/h3/token/google/refresh` |

- client_id/secret: `application.yml` `google.client-id` / `google.client-secret`
- scope: `https://www.googleapis.com/auth/adwords`, access_type: `offline`, prompt: `consent`
- 저장 컬렉션: `google_token` (key: `"google"`)
- Google Cloud Console → OAuth 2.0 Credentials → Authorized redirect URIs 등록 필요

---

## 이식 현황 (PHP → Java)

### 네이버 (Naver SA)

| PHP 원본 | Java Job | 상태 |
|---|---|---|
| `navermasterreport.php` | `NaverMasterReportJob` | ✅ |
| `navercampaigndaycollection.php` | `NaverCampaignDayCollectionJob` | ✅ |
| `navercampaignhourcollection.php` | `NaverCampaignHourCollectionJob` | ✅ |
| `naveradgroupdaycollection.php` | `NaverAdGroupDayCollectionJob` | ✅ |
| `naveraddaycollection.php` | `NaverAdDayCollectionJob` | ✅ |
| `naversaddaycollection.php` | `NaverShoppingAdDayCollectionJob` | ✅ |
| `naverstatereport.php` | `NaverStateReportJob` | ✅ |
| `naverconvtypecollection.php` | `NaverConvTypeJob` | ✅ |
| `naverbudgetalarmcollection.php` | — | ❌ 미이식 |

### 네이버 GFA

| PHP 원본 | Java Job | 상태 |
|---|---|---|
| `navergfamasterreport.php` | `NaverGfaMasterJob` | ✅ |
| `navergfacampaigndaycollection.php` | `NaverGfaCampaignDayCollectionJob` | ✅ |
| `navergfaadgroupdaycollection.php` | `NaverGfaAdgroupDayCollectionJob` | ✅ |
| `navergfaaddaycollection.php` | `NaverGfaAdDayCollectionJob` | ✅ |
| `navergfabudgetalarm.php` | `NaverGfaBudgetAlarmJob` | ✅ |
| `navergfaconvtype.php` | `NaverGfaConvTypeJob` | ✅ |

### 카카오 SA

| PHP 원본 | Java Job | 상태 |
|---|---|---|
| `kakaosamaster.php` | `KakaoSaMasterJob` | ✅ |
| `kakaosacampaignday.php` | `KakaoSaCampaignDayJob` | ✅ |
| `kakaosacampaignhour.php` | `KakaoSaCampaignHourJob` | ✅ |
| `kakaosaadgroupday.php` | `KakaoSaAdGroupDayJob` | ✅ |
| `kakaosaadday.php` | `KakaoSaAdDayJob` | ✅ |
| `kakaosakey.php` | `KakaoSaKeywordDayJob` | ✅ |
| `kakaosabudgetalarm.php` | `KakaoSaBudgetAlarmJob` | ✅ |

### 카카오 MO

| PHP 원본 | Java Job | 상태 |
|---|---|---|
| `kakaomomaster.php` | `KakaoMoMasterJob` | ✅ |
| `kakaomocampaignday.php` | `KakaoMoCampaignDayJob` | ✅ |
| `kakaomocampaignhour.php` | `KakaoMoCampaignHourJob` | ✅ |
| `kakaomoadgroupday.php` | `KakaoMoAdGroupDayJob` | ✅ |
| `kakaomoadday.php` | `KakaoMoAdDayJob` | ✅ |
| `kakaomobudgetalarm.php` | `KakaoMoBudgetAlarmJob` | ✅ |

### 구글 (Google)

| PHP 원본 | Java Job | 상태 |
|---|---|---|
| `googlemasterreport.php` | `GoogleMasterJob` | ✅ |
| `googlecampaigndaycollection.php` | `GoogleCampaignDayJob` | ✅ |
| `googlecampaignhourcollection.php` | `GoogleCampaignHourJob` | ✅ |
| `googleadgroupdaycollection.php` | `GoogleAdGroupDayJob` | ✅ |
| `googleaddaycollection.php` | `GoogleAdDayJob` | ✅ |
| `googlekeyworddaycollection.php` | `GoogleKeywordDayJob` | ✅ |
| `googletargetdaycollection.php` | — | ❌ PHP DB INSERT 없음 (의도적 미이식) |

---

## 핵심 설계 원칙

1. **DB 컬럼명 동일 유지** — PHP 원본과 동일한 컬럼명 사용 (매체 간 통일 유지)
2. **TSV 처리** — PHP: 파일 다운로드 후 읽기 → Java: `byte[]` 메모리 처리 (디스크 I/O 없음)
3. **skip 대상** — `admin`, `dydrp123` userId는 항상 건너뜀
4. **자동 날짜 모드** — 기본 D-1, D-3, D-5 + 최근 7일 gap 체크 (데이터 없는 날 재수집)
5. **VAT** — TSV cost 필드는 `× 1.1` 적용 (StateReport 한정, ConvType 미적용)
6. **bulk insert** — MyBatis `<foreach>` 500행 청크
7. **delta 증분** — 마스터 수집 시 `naver_master_delta` (MongoDB)로 updateTime 비교 → 변경분만 수집

---

## 신규 계정 자동 초기화

5개 매체별 스케줄러가 **10분 주기**로 MongoDB `h3_account`를 폴링한다.  
각 매체 컬렉션에 advkey/adAccountId가 없는 계정을 신규로 판별하고 마스터 수집 후 MQ를 발행한다.

| 스케줄러 | 감지 대상 | 초기화 순서 |
|---|---|---|
| `NaverNewAccountScheduler` | 네이버 SA | 마스터 동기 → MQ 7개 (CampaignDaily, CampaignHour, AdGroupDaily, AdDaily, ShoppingDaily, StateReport, ConvType) |
| `NaverGfaNewAccountScheduler` | 네이버 GFA | 마스터 동기 → MQ 발행 |
| `KakaoSaNewAccountScheduler` | 카카오 SA | 마스터 동기 → MQ 발행 |
| `KakaoMoNewAccountScheduler` | 카카오 MO | 마스터 동기 → MQ 발행 |
| `GoogleNewAccountScheduler` | 구글 | 마스터 동기 → MQ 발행 |

- 한 번 시도한 계정은 앱 재시작 전까지 재시도하지 않음 (`initiated` Set으로 차단)
- 실패 후 재시도: 앱 재시작 또는 수동 REST 호출 (`/api/collector/{media}/master/{userId}`)

---

## 배포

```bash
# 1. 컨테이너 정지
docker compose down

# 2. (선택) RabbitMQ 큐 purge — http://192.168.100.12:15672 관리 콘솔에서 수동

# 3. 코드 pull + 재빌드 + 재시작
git pull && docker compose build --no-cache h3-java && docker compose up -d h3-java
```

`--no-cache` 없이 `up -d`만 실행하면 이전 캐시 이미지를 그대로 사용한다.

```bash
# 실시간 로그
tail -f /opt/h3-java/logs/h3-java.log

# 에러 로그
tail -f /opt/h3-java/logs/h3-java-error.log
```
