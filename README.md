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
POST /api/collector/naver/{job-type}
POST /api/collector/naver/{job-type}/{userId}
POST /api/collector/naver/{job-type}/{userId}/range?from=YYYY-MM-DD&to=YYYY-MM-DD
```
job-type: `master`, `campaign-daily`, `campaign-hour`, `adgroup-daily`, `ad-daily`, `shopping-daily`, `state-report`, `conv-type`

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
| `navergfa*.php` | — | ❌ 미이식 |

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

`NaverNewAccountScheduler`가 10분 주기로 `h3_account`를 폴링한다.  
MongoDB `naver_campaign`에 advkey가 없는 계정을 신규로 판별하고 아래 순서로 자동 초기화한다.

1. `NaverMasterReportJob.collectForUserId(userId, false)` — 마스터 동기 실행
2. MQ 7개 발행 — CampaignDaily, CampaignHour, AdGroupDaily, AdDaily, ShoppingDaily, StateReport, ConvType

---

## 서버 정보 (네이버 클라우드)

| 항목 | 내용 |
|---|---|
| 서버명 | heeil-h3 |
| 원격 접속 | 101.101.163.90:9919 |
| 공인 IP | 49.50.167.235 |
| 내부 IP | 192.168.100.31 |
| 계정 | root / heeil-h3 |
| ACG | ncloud-default-acg |
| 스펙 | [High-Memory] 8vCPU, 64GB Mem [g1] |

---

## 배포

```bash
# 코드 변경 후 이미지 재빌드 필수
docker compose up -d --build
```

`docker-compose.yml`에 `build: .` 설정이 되어 있어 `--build` 없이 `up -d`만 실행하면 이전 이미지를 그대로 사용한다.
