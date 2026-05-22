# h3-java

PHP 크론 스크립트(`h3-백엔드`)를 Spring Boot로 이식한 광고 데이터 수집 서비스.  
네이버·카카오·구글 광고 플랫폼 데이터를 수집해 MySQL과 MongoDB에 저장한다.

---

## 기술 스택

| 분류 | 기술 |
|---|---|
| Language | Java 17 |
| Framework | Spring Boot 3 |
| ORM | MyBatis (XML mapper) |
| Message Queue | RabbitMQ |
| DB | MySQL (통계·마스터), MongoDB Atlas (raw 마스터·시간별·delta) |
| Build | Gradle |

---

## 아키텍처

```
[ REST API / Scheduler ]
        │
        ▼
  RabbitMQ (h3.collector DirectExchange)
        │
        ▼
  CollectorConsumer
        │
   ┌────┴────┐
   │         │
Master Job  Stat Job
   │         │
   ▼         ▼
MongoDB    MySQL + MongoDB
```

수집 트리거는 두 가지 경로로 들어온다.

1. **REST API** — `/api/collector/naver/{job-type}`
2. **Scheduler** — `CollectorScheduler` (cron, Asia/Seoul)

단일 유저(`/{userId}`) 또는 기간(`/{userId}/range`) 요청은 항상 **MQ 경유 비동기** 처리된다.  
Consumer에서 `hasRange()` 분기 후 `collectRange` 또는 `collectForUserId`를 호출한다.

---

## 패키지 구조

```
com.h3.h3_java
├── api/collector/          NaverCollectorController       (수집 트리거 REST)
├── batch/
│   ├── master/             NaverMasterReportJob           (캠페인·광고그룹·소재 구조 수집)
│   │                       NaverAdDetailJob               (소재 상세)
│   ├── stat/               NaverCampaignDayCollectionJob  (캠페인 일별)
│   │                       NaverCampaignHourCollectionJob (캠페인 시간별)
│   │                       NaverAdGroupDayCollectionJob   (광고그룹 일별)
│   │                       NaverAdDayCollectionJob        (소재 일별)
│   │                       NaverShoppingAdDayCollectionJob(쇼핑소재 일별)
│   │                       NaverStateReportJob            (키워드·타겟 TSV)
│   │                       NaverConvTypeJob               (전환유형 TSV)
│   └── scheduler/          CollectorScheduler             (정기 스케줄 → MQ)
│                           NaverNewAccountScheduler       (신규 계정 자동 초기화)
├── queue/
│   ├── message/            CollectorMessage
│   ├── producer/           CollectorProducer
│   └── consumer/           CollectorConsumer
├── media/naver/
│   ├── NaverApiClient      (HMAC-SHA256 서명, Semaphore(3), retry 5회)
│   ├── NaverTsvParser      (TSV byte[] 파싱)
│   ├── dto/
│   └── mapper/             NaverMasterReportMapper (MyBatis)
├── raw/mongo/
│   ├── NaverMasterMongoService   (마스터·delta MongoDB 저장)
│   └── NaverStatMongoService     (일별·시간별 MongoDB 저장)
└── common/config/          RabbitMQConfig
```

---

## 스케줄 (Asia/Seoul)

| 시각 | Job |
|---|---|
| 02:00 | 마스터 수집 (NaverMasterReportJob) |
| 03:00 | 캠페인 일별 |
| 03:30 | 캠페인 시간별 |
| 04:00 | 광고그룹 일별 |
| 04:30 | 소재 일별 |
| 04:45 | 쇼핑소재 일별 |
| 05:00 | StateReport (키워드·타겟) |
| 05:30 | 전환유형 |
| 매 10분 | 신규 계정 감지 (NaverNewAccountScheduler) |

---

## REST API

```
POST /api/collector/naver/{job-type}                    전체 수집
POST /api/collector/naver/{job-type}/{userId}           단일 유저 → MQ
POST /api/collector/naver/{job-type}/{userId}/range?from=YYYYMMDD&to=YYYYMMDD  기간 → MQ
```

`{job-type}` 목록: `master`, `campaign-daily`, `campaign-hour`, `adgroup-daily`, `ad-daily`, `shopping-daily`, `state-report`, `conv-type`

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

### 카카오 / 구글

| 대상 | 상태 |
|---|---|
| 카카오 SA, 카카오 Moment | ❌ 미이식 |
| Google 전 계열 | ❌ 미이식 |

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

## 배포

```bash
# 코드 변경 후 이미지 재빌드 필수
docker compose up -d --build
```

`docker-compose.yml`에 `build: .` 설정이 되어 있어 `--build` 없이 `up -d`만 실행하면 이전 이미지를 그대로 사용한다.
