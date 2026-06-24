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
| Message Queue | RabbitMQ (Spring AMQP) |
| DB | MySQL (네이버 통계), MongoDB Atlas (마스터·통계·계정·토큰) |
| AI | OpenAI gpt-4.1-mini |
| Build | Gradle |
| Etc | Lombok, Spring Security (JWT) |

---

## 아키텍처

시스템은 세 개의 독립적인 흐름으로 구성된다.

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
           MongoDB     MySQL(Naver) + MongoDB(Kakao/Google)
```

- 수집 트리거: Collector REST API(`/api/collector/**`) 또는 `CollectorScheduler`(cron)
- 단일 유저·기간 요청은 항상 **MQ 경유 비동기** 처리
- Consumer에서 `hasRange()` 분기 → `collectRange` 또는 `collectForUserId`
- 네이버 SA 통계: MySQL, 카카오·구글·GFA 통계: MongoDB

### 2. 조회 흐름 (Service Layer)

```
[ vue-h3 Frontend ]
        │  Authorization: Bearer <JWT>
        ▼
  Spring Security (JwtFilter + CORS)
        │
        ├──────────────────────────┐
        ▼                          ▼
  /v1/h3/app/**              /v1/h3/admin/**
  분석·대시보드 API           관리자·에이전시 API
        │                          │
        ▼                          ▼
     MongoDB                   MongoDB
  (통계·마스터·계정)         (users·share·adv·account)
        │
        ▼
   OpenAI API (aiinsight)
```

- `/v1/h3/app/**`, `/v1/h3/admin/**` 경로 전체 JWT 인증 필수
- `/v1/h3/token/**` 경로는 permitAll (OAuth 콜백)
- 계정·유저·공유 정보: MySQL 미사용, **MongoDB 전용**

### 3. 토큰 관리 흐름 (OAuth)

```
브라우저 → /v1/h3/token/{platform}/oauth → 플랫폼 인가 화면
                                                  │
                                           /callback?code=
                                                  │
                                           TokenController
                                                  │
                                           MongoDB (token 컬렉션)
                                                  │
                                    TokenManager (@Scheduled 30분 자동갱신)
```

---

## 패키지 구조

```
com.h3.h3_java
├── api/
│   ├── collector/      수집 트리거 REST 엔드포인트 (Naver/Kakao/Google)
│   ├── controller/     서비스·관리자 REST 엔드포인트
│   │   ├── AppController      대시보드·분석·리포트 (JWT 필요)
│   │   ├── AdminController    관리자·에이전시 API (JWT 필요)
│   │   └── TokenController    OAuth 토큰 발급·콜백·갱신 (permitAll)
│   ├── service/
│   │   ├── dashboard/  DashboardService, AiInsightService
│   │   ├── analysis/   CampaignReport/AdgroupReport/KeywordReport/AdReport
│   │   │               MediaReport/PeriodReport/ShoppingReport 등
│   │   └── admin/      AdminUserService (회원·공유·에이전시)
│   ├── dto/            DTO
│   └── mapper/         MyBatis mapper
├── auth/               JWT (JwtUtil, JwtFilter, SecurityConfig + CORS)
├── batch/
│   ├── master/         마스터 수집 Job + AdDetailJob (소재상세 병렬)
│   ├── stat/           일별·시간별·TSV 통계 수집 Job
│   └── scheduler/      CollectorScheduler + 5개 NewAccountScheduler + TokenManager
├── queue/
│   ├── message/        CollectorMessage (fromDate/toDate)
│   ├── producer/       CollectorProducer
│   └── consumer/       CollectorConsumer → Job 라우팅
├── media/
│   ├── naver/          NaverApiClient (HMAC-SHA256, Semaphore(3)), TSV 파서, DTO, Mapper
│   ├── kakao/          KakaoSaApiClient, KakaoMoApiClient, DTO
│   └── google/         GoogleAdsApiClient, DTO, Mapper
└── raw/mongo/          MongoDB 저장 서비스
    ├── AccountMongoService      h3_account (계정 정보 — 전 매체 공용)
    ├── UserMongoService         h3_users (로그인 회원)
    ├── ShareMongoService        h3_share (공유 마케터)
    ├── AdvMongoService          h3_adv (광고주 정보)
    ├── NaverMasterMongoService  네이버 SA 마스터
    ├── NaverGfaMasterMongoService / NaverGfaTokenMongoService
    ├── KakaoSaMasterMongoService / KakaoSaTokenMongoService / KakaoSaStatMongoService
    ├── KakaoMoMasterMongoService / KakaoMoTokenMongoService / KakaoMoStatMongoService
    └── GoogleTokenMongoService
```

---

## 스케줄 (Asia/Seoul)

매체별 독립 큐로 병렬 수집. **전 매체 08:30 완료** (마케터 09:30 출근 전).

| 시각 | Job |
|---|---|
| 01:00 | 네이버 GFA 마스터 |
| 02:00 | 네이버 SA 마스터 |
| 03:00 | 네이버 SA 캠페인 일별 / GFA 캠페인 일별 / 카카오 SA 마스터 |
| 03:30 | 네이버 SA 캠페인 시간별 / GFA 광고그룹 일별 |
| 04:00 | 네이버 SA 광고그룹 일별 / GFA 예산알람 / 카카오 SA 캠페인 일별 |
| 04:30 | 네이버 SA 소재 일별 / GFA 소재 일별 / 카카오 SA 캠페인 시간별 |
| 04:45 | 네이버 SA 쇼핑소재 일별 |
| 05:00 | 네이버 SA StateReport / GFA 전환유형 / 카카오 SA 광고그룹 일별 / 구글 마스터 |
| 05:30 | 네이버 SA 전환유형 / 카카오 SA 키워드 일별 / 카카오 MO 마스터 |
| 06:00 | 카카오 SA 소재 일별 / 구글 캠페인 일별 |
| 06:30 | 카카오 SA 예산알람 / 카카오 MO 캠페인 일별 / 구글 캠페인 시간별 |
| 07:00 | 카카오 MO 캠페인 시간별 / 구글 광고그룹 일별 |
| 07:30 | 카카오 MO 광고그룹 일별 / 구글 소재 일별 |
| 08:00 | 카카오 MO 소재 일별 / 구글 키워드 일별 |
| 08:30 | 카카오 MO 예산알람 |
| 매 10분 | 신규 계정 감지 (5개 매체 NewAccountScheduler) |

---

## 인증 (JWT)

`/v1/h3/app/**`, `/v1/h3/admin/**` 경로는 Bearer JWT 인증이 필요하다.

```
# 로그인
POST /v1/h3/auth/login
Body: { "userid": "...", "userpass": "..." }
Response: { "accessToken": "<JWT>" }

# 이후 모든 요청에 헤더 첨부
Authorization: Bearer <JWT>
```

- 토큰 유효기간: 24시간
- 비밀번호: SHA-256 해시 후 MongoDB `h3_users.user_pass` 비교
- CORS: 전 Origin 허용 (`allowedOriginPatterns=*`)

---

## REST API (수집)

```
# 네이버 SA
POST /api/collector/naver/{job-type}
POST /api/collector/naver/{job-type}/range?from=YYYY-MM-DD&to=YYYY-MM-DD
POST /api/collector/naver/{job-type}/{userId}
POST /api/collector/naver/{job-type}/{userId}/range?from=YYYY-MM-DD&to=YYYY-MM-DD
```
job-type: `master`, `campaign-daily`, `campaign-hour`, `adgroup-daily`, `ad-daily`, `shopping-daily`, `state-report`, `conv-type`, `ad-detail`

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
job-type: `master`, `campaign-daily`, `campaign-hour`, `adgroup-daily`, `ad-daily`, `keyword-daily`, `budget-alarm`, `ad-detail`

```
# 카카오 MO
POST /api/collector/kakao/mo/{job-type}
POST /api/collector/kakao/mo/{job-type}/{userId}
POST /api/collector/kakao/mo/{job-type}/{userId}/range?from=YYYY-MM-DD&to=YYYY-MM-DD
```
job-type: `master`, `campaign-daily`, `campaign-hour`, `adgroup-daily`, `ad-daily`, `budget-alarm`, `ad-detail`

```
# 구글
POST /api/collector/google/{job-type}
POST /api/collector/google/{job-type}/{userId}
POST /api/collector/google/{job-type}/{userId}/range?from=YYYY-MM-DD&to=YYYY-MM-DD
```
job-type: `master`, `campaign-daily`, `campaign-hour`, `adgroup-daily`, `ad-daily`, `keyword-daily`

---

## 서비스 레이어 API

### 대시보드 · 분석 (`/v1/h3/app/**`) — JWT 필요

```
GET  /v1/h3/app/dashboard/{endpoint}
POST /v1/h3/app/dashboard/aiinsight
POST /v1/h3/app/dashboard/aiinsight_followup
GET  /v1/h3/app/analysis/{endpoint}
```

| 구분 | 엔드포인트 | 상태 |
|---|---|---|
| Dashboard | `summarymedia` | ✅ |
| Dashboard | `summary` | ✅ |
| Dashboard | `period` | ✅ |
| Dashboard | `aiinsight` | ✅ (OpenAI gpt-4.1-mini) |
| Dashboard | `aiinsight_followup` | ✅ (OpenAI gpt-4.1-mini) |
| Analysis | `campaignreport` | ✅ |
| Analysis | `adgroupreport` | ✅ |
| Analysis | `keywordreport` | ✅ |
| Analysis | `adreport` | ✅ |
| Analysis | `mediareport` | ✅ |
| Analysis | `keywordrereport` | ✅ |
| Analysis | `periodreport` | ✅ |
| Shopping | `shoppingreport` | ✅ |
| Shopping | `adgroupshoppingreport` | ✅ |
| Shopping | `campaignshoppingreport` | ✅ |
| Analysis | `targetreport` | ⏳ 대기 |

### 관리자 · 에이전시 (`/v1/h3/admin/**`) — JWT 필요

```
GET  /v1/h3/admin/agent                  # 에이전트 목록
POST /v1/h3/admin/agent/status           # 에이전트 상태 변경
GET  /v1/h3/admin/my-users               # 내 광고주 목록
GET  /v1/h3/admin/beshared-users         # 공유받은 광고주
POST /v1/h3/admin/favorites              # 즐겨찾기 토글
POST /v1/h3/admin/userlink               # 바로가기 (target JWT 발급)
POST /v1/h3/admin/share-update           # 공유 마케터 업데이트
POST /v1/h3/admin/userregister           # 광고주 등록
POST /v1/h3/admin/account-register       # 광고 계정 등록
GET  /v1/h3/admin/agency-users           # 에이전시 광고주 + 매체별 일별 비용
GET  /v1/h3/admin/agency-beshared-users  # 에이전시 공유 광고주 + 비용
GET  /v1/h3/admin/agency-alarms          # 통합 예산 알람 (admin/marketer 분기)
```

---

## OAuth 토큰 관리

`/v1/h3/token/**` 경로는 `permitAll` — JWT 인증 없이 브라우저에서 직접 호출.  
토큰 발급 후 TokenManager가 30분 주기로 자동 갱신.

### 네이버 GFA

| 구분 | URL |
|---|---|
| 최초 발급 | `GET https://api.heeil.com/java/v1/h3/token/naver-gfa/oauth` |
| 콜백 (자동) | `GET https://api.heeil.com/java/v1/h3/token/naver-gfa/callback?code=&state=` |
| 수동 갱신 | `POST https://api.heeil.com/java/v1/h3/token/naver-gfa/refresh` |

- client_id/secret: MongoDB `h3_account` admin 계정 `account_gfa` / `account_naver_secret`
- 저장: `naver_gfa_token` (key: `"navergfa"`)

### 카카오 SA

| 구분 | URL |
|---|---|
| 최초 발급 | `GET https://api.heeil.com/java/v1/h3/token/kakao-sa/oauth` |
| 콜백 (자동) | `GET https://api.heeil.com/java/v1/h3/token/kakao-sa/callback?code=` |
| 수동 갱신 | `POST https://api.heeil.com/java/v1/h3/token/kakao-sa/refresh` |

- `&prompt=none` 포함 (자동 재인가)
- 저장: `kakao_sa_token` (key: `"kakaosa"`)

### 카카오 MO

| 구분 | URL |
|---|---|
| 최초 발급 | `GET https://api.heeil.com/java/v1/h3/token/kakao-mo/oauth` |
| 콜백 (자동) | `GET https://api.heeil.com/java/v1/h3/token/kakao-mo/callback?code=` |
| 수동 갱신 | `POST https://api.heeil.com/java/v1/h3/token/kakao-mo/refresh` |

- client-id: SA와 **다른** 앱 (`kakao.mo.client-id`)
- 저장: `kakao_mo_token` (key: `"kakaomo"`)

### 구글

| 구분 | URL |
|---|---|
| 최초 발급 | `GET https://api.heeil.com/java/v1/h3/token/google/oauth` |
| 콜백 (자동) | `GET https://api.heeil.com/java/v1/h3/token/google/callback?code=` |
| 수동 갱신 | `POST https://api.heeil.com/java/v1/h3/token/google/refresh` |

- scope: `https://www.googleapis.com/auth/adwords`
- 저장: `google_token` (key: `"google"`)

---

## 데이터 저장소

### MySQL (네이버 SA 통계 전용)

> 계정·회원·공유 정보는 모두 MongoDB로 이관 완료. MySQL은 네이버 SA 통계 테이블만 사용.

| 테이블 | 설명 |
|---|---|
| `h3_campaign_daily_naver` | 네이버 SA 캠페인 일별 통계 |
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

### MongoDB Atlas

#### 시스템 / 계정

| 컬렉션 | 설명 |
|---|---|
| `h3_account` | 매체 API 계정 정보 (key: user_id) |
| `h3_users` | 로그인 회원 정보 (key: user_id) |
| `h3_share` | 공유 마케터 정보 (key: user_manager) |
| `h3_adv` | 광고주 정보 (key: user_id) |

#### 네이버 SA

| 컬렉션 | 설명 |
|---|---|
| `naver_campaign` | 캠페인 마스터 |
| `naver_campaign_budget` | 캠페인 예산 |
| `naver_adgroup` | 광고그룹 마스터 |
| `naver_adgroup_budget` | 광고그룹 예산 |
| `naver_ad` | 소재 마스터 |
| `naver_keyword` | 키워드 마스터 |
| `naver_adextension` | 광고 확장소재 |
| `naver_shopping_product` | 쇼핑소재 마스터 |
| `naver_master_delta` | 마스터 delta 추적 (증분 수집용) |
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

#### 네이버 GFA

| 컬렉션 | 설명 |
|---|---|
| `naver_gfa_token` | GFA OAuth 토큰 |
| `naver_gfa_campaign` | GFA 캠페인 마스터 |
| `naver_gfa_adgroup` | GFA 광고그룹 마스터 |
| `naver_gfa_ad` | GFA 소재 마스터 |
| `naver_gfa_campaign_daily` | GFA 캠페인 일별 통계 |
| `naver_gfa_adgroup_daily` | GFA 광고그룹 일별 통계 |
| `naver_gfa_ad_daily` | GFA 소재 일별 통계 |

#### 카카오 SA

| 컬렉션 | 설명 |
|---|---|
| `kakao_sa_token` | 카카오 SA OAuth 토큰 |
| `kakao_sa_campaign` | 캠페인 마스터 |
| `kakao_sa_adgroup` | 광고그룹 마스터 |
| `kakao_sa_ad` | 소재 마스터 |
| `kakao_sa_keyword` | 키워드 마스터 |
| `kakao_sa_campaign_daily` | 캠페인 일별 통계 |
| `kakao_sa_campaign_hour` | 캠페인 시간별 통계 |
| `kakao_sa_adgroup_daily` | 광고그룹 일별 통계 |
| `kakao_sa_ad_daily` | 소재 일별 통계 |
| `kakao_sa_keyword_daily` | 키워드 일별 통계 |
| `kakao_sa_budget_alarm` | 예산 알람 |

#### 카카오 MO

| 컬렉션 | 설명 |
|---|---|
| `kakao_mo_token` | 카카오 MO OAuth 토큰 |
| `kakao_mo_campaign` | 캠페인 마스터 |
| `kakao_mo_adgroup` | 광고그룹 마스터 |
| `kakao_mo_ad` | 소재 마스터 |
| `kakao_mo_campaign_daily` | 캠페인 일별 통계 |
| `kakao_mo_campaign_hour` | 캠페인 시간별 통계 |
| `kakao_mo_adgroup_daily` | 광고그룹 일별 통계 |
| `kakao_mo_ad_daily` | 소재 일별 통계 |
| `kakao_mo_budget_alarm` | 예산 알람 |

#### 구글

| 컬렉션 | 설명 |
|---|---|
| `google_token` | 구글 OAuth 토큰 |
| `google_campaign` | 캠페인 마스터 |
| `google_adgroup` | 광고그룹 마스터 |
| `google_keyword` | 키워드 마스터 |
| `google_ad` | 소재 마스터 |
| `google_campaign_daily` | 캠페인 일별 통계 |
| `google_campaign_hour` | 캠페인 시간별 통계 |
| `google_adgroup_daily` | 광고그룹 일별 통계 |
| `google_ad_daily` | 소재 일별 통계 |
| `google_keyword_daily` | 키워드 일별 통계 |

---

## 이식 현황 (PHP → Java)

### 네이버 SA

| PHP 원본 | Java Job | 상태 |
|---|---|---|
| `navermasterreport.php` | `NaverMasterReportJob` + `NaverAdDetailJob` | ✅ |
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
| `kakaosamaster.php` | `KakaoSaMasterJob` + `KakaoSaAdDetailJob` | ✅ |
| `kakaosacampaignday.php` | `KakaoSaCampaignDayJob` | ✅ |
| `kakaosacampaignhour.php` | `KakaoSaCampaignHourJob` | ✅ |
| `kakaosaadgroupday.php` | `KakaoSaAdGroupDayJob` | ✅ |
| `kakaosaadday.php` | `KakaoSaAdDayJob` | ✅ |
| `kakaosakey.php` | `KakaoSaKeywordDayJob` | ✅ |
| `kakaosabudgetalarm.php` | `KakaoSaBudgetAlarmJob` | ✅ |

### 카카오 MO

| PHP 원본 | Java Job | 상태 |
|---|---|---|
| `kakaomomaster.php` | `KakaoMoMasterJob` + `KakaoMoAdDetailJob` | ✅ |
| `kakaomocampaignday.php` | `KakaoMoCampaignDayJob` | ✅ |
| `kakaomocampaignhour.php` | `KakaoMoCampaignHourJob` | ✅ |
| `kakaomoadgroupday.php` | `KakaoMoAdGroupDayJob` | ✅ |
| `kakaomoadday.php` | `KakaoMoAdDayJob` | ✅ |
| `kakaomobudgetalarm.php` | `KakaoMoBudgetAlarmJob` | ✅ |

### 구글

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
2. **계정 정보 MongoDB 전용** — `h3_account`, `h3_users`, `h3_share`, `h3_adv` 모두 MongoDB. MySQL 미사용
3. **TSV 처리** — PHP: 파일 다운로드 → Java: `byte[]` 메모리 처리 (디스크 I/O 없음)
4. **skip 대상** — `admin`, `dydrp123` userId는 항상 건너뜀
5. **자동 날짜 모드** — 기본 D-1, D-3, D-5 + 최근 7일 gap 체크 (데이터 없는 날 재수집)
6. **VAT** — TSV cost 필드는 `× 1.1` 적용 (StateReport 한정, ConvType 미적용)
7. **bulk insert** — MyBatis `<foreach>` 500행 청크
8. **delta 증분** — 마스터 수집 시 `naver_master_delta` (MongoDB)로 updateTime 비교 → 변경분만 수집
9. **AdDetail 분리** — 마스터에서 소재상세 API 호출 제거 → `KakaoSaAdDetailJob` / `KakaoMoAdDetailJob` / `NaverAdDetailJob`으로 분리 (N+1 해결, Virtual Thread 병렬 처리)

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

# 매체별 필터
grep "\[KAKAO-SA\]" /opt/h3-java/logs/h3-java.log
grep "\[GOOGLE\]" /opt/h3-java/logs/h3-java.log
```
