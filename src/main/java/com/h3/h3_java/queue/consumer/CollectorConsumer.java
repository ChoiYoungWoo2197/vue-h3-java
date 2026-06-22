package com.h3.h3_java.queue.consumer;

import com.h3.h3_java.batch.master.GoogleMasterJob;
import com.h3.h3_java.batch.stat.GoogleCampaignDayJob;
import com.h3.h3_java.batch.stat.GoogleCampaignHourJob;
import com.h3.h3_java.batch.stat.GoogleAdGroupDayJob;
import com.h3.h3_java.batch.stat.GoogleAdDayJob;
import com.h3.h3_java.batch.stat.GoogleKeywordDayJob;
import com.h3.h3_java.batch.master.KakaoMoAdDetailJob;
import com.h3.h3_java.batch.master.KakaoMoMasterJob;
import com.h3.h3_java.batch.master.KakaoSaAdDetailJob;
import com.h3.h3_java.batch.master.KakaoSaMasterJob;
import com.h3.h3_java.batch.stat.KakaoMoCampaignDayJob;
import com.h3.h3_java.batch.stat.KakaoMoCampaignHourJob;
import com.h3.h3_java.batch.stat.KakaoMoAdGroupDayJob;
import com.h3.h3_java.batch.stat.KakaoMoAdDayJob;
import com.h3.h3_java.batch.stat.KakaoMoBudgetAlarmJob;
import com.h3.h3_java.batch.stat.KakaoSaCampaignDayJob;
import com.h3.h3_java.batch.stat.KakaoSaCampaignHourJob;
import com.h3.h3_java.batch.stat.KakaoSaAdGroupDayJob;
import com.h3.h3_java.batch.stat.KakaoSaKeywordDayJob;
import com.h3.h3_java.batch.stat.KakaoSaAdDayJob;
import com.h3.h3_java.batch.stat.KakaoSaBudgetAlarmJob;
import com.h3.h3_java.batch.master.NaverAdDetailJob;
import com.h3.h3_java.batch.master.NaverGfaMasterJob;
import com.h3.h3_java.batch.stat.NaverGfaCampaignDayCollectionJob;
import com.h3.h3_java.batch.master.NaverMasterReportJob;
import com.h3.h3_java.batch.stat.NaverAdDayCollectionJob;
import com.h3.h3_java.batch.stat.NaverAdGroupDayCollectionJob;
import com.h3.h3_java.batch.stat.NaverCampaignDayCollectionJob;
import com.h3.h3_java.batch.stat.NaverCampaignHourCollectionJob;
import com.h3.h3_java.batch.stat.NaverConvTypeJob;
import com.h3.h3_java.batch.stat.NaverGfaAdDayCollectionJob;
import com.h3.h3_java.batch.stat.NaverGfaAdgroupDayCollectionJob;
import com.h3.h3_java.batch.stat.NaverGfaBudgetAlarmJob;
import com.h3.h3_java.batch.stat.NaverGfaConvTypeJob;
import com.h3.h3_java.batch.stat.NaverShoppingAdDayCollectionJob;
import com.h3.h3_java.batch.stat.NaverStateReportJob;
import com.h3.h3_java.common.config.RabbitMQConfig;
import com.h3.h3_java.queue.message.CollectorMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class CollectorConsumer {

    private final NaverMasterReportJob naverMasterReportJob;
    private final NaverAdDetailJob naverAdDetailJob;
    private final NaverGfaMasterJob naverGfaMasterJob;
    private final NaverGfaCampaignDayCollectionJob naverGfaCampaignDayCollectionJob;
    private final NaverCampaignDayCollectionJob naverCampaignDayCollectionJob;
    private final NaverCampaignHourCollectionJob naverCampaignHourCollectionJob;
    private final NaverAdGroupDayCollectionJob naverAdGroupDayCollectionJob;
    private final NaverStateReportJob naverStateReportJob;
    private final NaverAdDayCollectionJob naverAdDayCollectionJob;
    private final NaverShoppingAdDayCollectionJob naverShoppingAdDayCollectionJob;
    private final NaverConvTypeJob naverConvTypeJob;
    private final NaverGfaAdDayCollectionJob naverGfaAdDayCollectionJob;
    private final NaverGfaAdgroupDayCollectionJob naverGfaAdgroupDayCollectionJob;
    private final NaverGfaBudgetAlarmJob naverGfaBudgetAlarmJob;
    private final NaverGfaConvTypeJob naverGfaConvTypeJob;
    private final KakaoSaAdDetailJob     kakaoSaAdDetailJob;
    private final KakaoMoAdDetailJob     kakaoMoAdDetailJob;
    private final KakaoMoMasterJob       kakaoMoMasterJob;
    private final KakaoMoCampaignDayJob  kakaoMoCampaignDayJob;
    private final KakaoMoCampaignHourJob kakaoMoCampaignHourJob;
    private final KakaoMoAdGroupDayJob   kakaoMoAdGroupDayJob;
    private final KakaoMoAdDayJob        kakaoMoAdDayJob;
    private final KakaoMoBudgetAlarmJob  kakaoMoBudgetAlarmJob;
    private final GoogleMasterJob        googleMasterJob;
    private final GoogleCampaignDayJob   googleCampaignDayJob;
    private final GoogleCampaignHourJob  googleCampaignHourJob;
    private final GoogleAdGroupDayJob    googleAdGroupDayJob;
    private final GoogleAdDayJob         googleAdDayJob;
    private final GoogleKeywordDayJob    googleKeywordDayJob;
    private final KakaoSaMasterJob       kakaoSaMasterJob;
    private final KakaoSaCampaignDayJob kakaoSaCampaignDayJob;
    private final KakaoSaCampaignHourJob kakaoSaCampaignHourJob;
    private final KakaoSaAdGroupDayJob  kakaoSaAdGroupDayJob;
    private final KakaoSaKeywordDayJob  kakaoSaKeywordDayJob;
    private final KakaoSaAdDayJob       kakaoSaAdDayJob;
    private final KakaoSaBudgetAlarmJob kakaoSaBudgetAlarmJob;

    @RabbitListener(queues = RabbitMQConfig.QUEUE_NAVER_MASTER, concurrency = "5")
    public void consumeNaverMaster(CollectorMessage msg) {
        log.info("[MQ][RECV] NAVER MASTER userId={} customerId={}", msg.getUserId(), msg.getCustomerId());
        try {
            naverMasterReportJob.collectForUserId(msg.getUserId(), msg.isForce());
        } catch (Exception e) {
            log.error("[MQ][ERROR] NAVER MASTER userId={} error={}", msg.getUserId(), e.getMessage(), e);
        }
    }

    @RabbitListener(queues = RabbitMQConfig.QUEUE_NAVER_AD_DETAIL, concurrency = "5")
    public void consumeNaverAdDetail(CollectorMessage msg) {
        log.info("[MQ][RECV] NAVER AD DETAIL userId={} customerId={}", msg.getUserId(), msg.getCustomerId());
        try {
            naverAdDetailJob.collectForUserId(msg.getUserId());
        } catch (Exception e) {
            log.error("[MQ][ERROR] NAVER AD DETAIL userId={} error={}", msg.getUserId(), e.getMessage(), e);
        }
    }

    @RabbitListener(queues = RabbitMQConfig.QUEUE_NAVER_CAMPAIGN_DAILY, concurrency = "3")
    public void consumeNaverCampaignDaily(CollectorMessage msg) {
        log.info("[MQ][RECV] NAVER CAMPAIGN DAILY userId={} customerId={}", msg.getUserId(), msg.getCustomerId());
        try {
            if (hasRange(msg)) {
                naverCampaignDayCollectionJob.collectRange(msg.getUserId(), msg.getFromDate(), msg.getToDate());
            } else {
                naverCampaignDayCollectionJob.collectForUserId(msg.getUserId());
            }
        } catch (Exception e) {
            log.error("[MQ][ERROR] NAVER CAMPAIGN DAILY userId={} error={}", msg.getUserId(), e.getMessage(), e);
        }
    }

    @RabbitListener(queues = RabbitMQConfig.QUEUE_NAVER_CAMPAIGN_HOUR, concurrency = "3")
    public void consumeNaverCampaignHour(CollectorMessage msg) {
        log.info("[MQ][RECV] NAVER CAMPAIGN HOUR userId={} customerId={}", msg.getUserId(), msg.getCustomerId());
        try {
            if (hasRange(msg)) {
                naverCampaignHourCollectionJob.collectRange(msg.getUserId(), msg.getFromDate(), msg.getToDate());
            } else {
                naverCampaignHourCollectionJob.collectForUserId(msg.getUserId());
            }
        } catch (Exception e) {
            log.error("[MQ][ERROR] NAVER CAMPAIGN HOUR userId={} error={}", msg.getUserId(), e.getMessage(), e);
        }
    }

    @RabbitListener(queues = RabbitMQConfig.QUEUE_NAVER_ADGROUP_DAILY, concurrency = "3")
    public void consumeNaverAdGroupDaily(CollectorMessage msg) {
        log.info("[MQ][RECV] NAVER ADGROUP DAILY userId={} customerId={}", msg.getUserId(), msg.getCustomerId());
        try {
            if (hasRange(msg)) {
                naverAdGroupDayCollectionJob.collectRange(msg.getUserId(), msg.getFromDate(), msg.getToDate());
            } else {
                naverAdGroupDayCollectionJob.collectForUserId(msg.getUserId());
            }
        } catch (Exception e) {
            log.error("[MQ][ERROR] NAVER ADGROUP DAILY userId={} error={}", msg.getUserId(), e.getMessage(), e);
        }
    }

    @RabbitListener(queues = RabbitMQConfig.QUEUE_NAVER_STATE_REPORT, concurrency = "3")
    public void consumeNaverStateReport(CollectorMessage msg) {
        log.info("[MQ][RECV] NAVER STATE REPORT userId={} customerId={}", msg.getUserId(), msg.getCustomerId());
        try {
            if (hasRange(msg)) {
                naverStateReportJob.collectRange(msg.getUserId(), msg.getFromDate(), msg.getToDate());
            } else {
                naverStateReportJob.collectForUserId(msg.getUserId());
            }
        } catch (Exception e) {
            log.error("[MQ][ERROR] NAVER STATE REPORT userId={} error={}", msg.getUserId(), e.getMessage(), e);
        }
    }

    @RabbitListener(queues = RabbitMQConfig.QUEUE_NAVER_AD_DAILY, concurrency = "3")
    public void consumeNaverAdDaily(CollectorMessage msg) {
        log.info("[MQ][RECV] NAVER AD DAILY userId={} customerId={}", msg.getUserId(), msg.getCustomerId());
        try {
            if (hasRange(msg)) {
                naverAdDayCollectionJob.collectRange(msg.getUserId(), msg.getFromDate(), msg.getToDate());
            } else {
                naverAdDayCollectionJob.collectForUserId(msg.getUserId());
            }
        } catch (Exception e) {
            log.error("[MQ][ERROR] NAVER AD DAILY userId={} error={}", msg.getUserId(), e.getMessage(), e);
        }
    }

    @RabbitListener(queues = RabbitMQConfig.QUEUE_NAVER_SHOPPING_DAILY, concurrency = "3")
    public void consumeNaverShoppingDaily(CollectorMessage msg) {
        log.info("[MQ][RECV] NAVER SHOPPING DAILY userId={} customerId={}", msg.getUserId(), msg.getCustomerId());
        try {
            if (hasRange(msg)) {
                naverShoppingAdDayCollectionJob.collectRange(msg.getUserId(), msg.getFromDate(), msg.getToDate());
            } else {
                naverShoppingAdDayCollectionJob.collectForUserId(msg.getUserId());
            }
        } catch (Exception e) {
            log.error("[MQ][ERROR] NAVER SHOPPING DAILY userId={} error={}", msg.getUserId(), e.getMessage(), e);
        }
    }

    @RabbitListener(queues = RabbitMQConfig.QUEUE_NAVER_CONV_TYPE, concurrency = "3")
    public void consumeNaverConvType(CollectorMessage msg) {
        log.info("[MQ][RECV] NAVER CONV TYPE userId={} customerId={}", msg.getUserId(), msg.getCustomerId());
        try {
            if (hasRange(msg)) {
                naverConvTypeJob.collectRange(msg.getUserId(), msg.getFromDate(), msg.getToDate());
            } else {
                naverConvTypeJob.collectForUserId(msg.getUserId());
            }
        } catch (Exception e) {
            log.error("[MQ][ERROR] NAVER CONV TYPE userId={} error={}", msg.getUserId(), e.getMessage(), e);
        }
    }

    @RabbitListener(queues = RabbitMQConfig.QUEUE_NAVER_GFA_CAMPAIGN_DAILY, concurrency = "3")
    public void consumeNaverGfaCampaignDaily(CollectorMessage msg) {
        log.info("[MQ][RECV] NAVER GFA CAMPAIGN DAILY userId={}", msg.getUserId());
        try {
            if (hasRange(msg)) {
                naverGfaCampaignDayCollectionJob.collectRange(msg.getUserId(), msg.getFromDate(), msg.getToDate());
            } else {
                naverGfaCampaignDayCollectionJob.collectForUserId(msg.getUserId());
            }
        } catch (Exception e) {
            log.error("[MQ][ERROR] NAVER GFA CAMPAIGN DAILY userId={} error={}", msg.getUserId(), e.getMessage(), e);
        }
    }

    @RabbitListener(queues = RabbitMQConfig.QUEUE_NAVER_GFA_MASTER, concurrency = "3")
    public void consumeNaverGfaMaster(CollectorMessage msg) {
        log.info("[MQ][RECV] NAVER GFA MASTER userId={}", msg.getUserId());
        try {
            naverGfaMasterJob.collectForUserId(msg.getUserId());
        } catch (Exception e) {
            log.error("[MQ][ERROR] NAVER GFA MASTER userId={} error={}", msg.getUserId(), e.getMessage(), e);
        }
    }

    @RabbitListener(queues = RabbitMQConfig.QUEUE_NAVER_GFA_AD_DAILY, concurrency = "3")
    public void consumeNaverGfaAdDaily(CollectorMessage msg) {
        log.info("[MQ][RECV] NAVER GFA AD DAILY userId={}", msg.getUserId());
        try {
            if (hasRange(msg)) {
                naverGfaAdDayCollectionJob.collectRange(msg.getUserId(), msg.getFromDate(), msg.getToDate());
            } else {
                naverGfaAdDayCollectionJob.collectForUserId(msg.getUserId());
            }
        } catch (Exception e) {
            log.error("[MQ][ERROR] NAVER GFA AD DAILY userId={} error={}", msg.getUserId(), e.getMessage(), e);
        }
    }

    @RabbitListener(queues = RabbitMQConfig.QUEUE_NAVER_GFA_ADGROUP_DAILY, concurrency = "3")
    public void consumeNaverGfaAdgroupDaily(CollectorMessage msg) {
        log.info("[MQ][RECV] NAVER GFA ADGROUP DAILY userId={}", msg.getUserId());
        try {
            if (hasRange(msg)) {
                naverGfaAdgroupDayCollectionJob.collectRange(msg.getUserId(), msg.getFromDate(), msg.getToDate());
            } else {
                naverGfaAdgroupDayCollectionJob.collectForUserId(msg.getUserId());
            }
        } catch (Exception e) {
            log.error("[MQ][ERROR] NAVER GFA ADGROUP DAILY userId={} error={}", msg.getUserId(), e.getMessage(), e);
        }
    }

    @RabbitListener(queues = RabbitMQConfig.QUEUE_NAVER_GFA_BUDGET_ALARM, concurrency = "3")
    public void consumeNaverGfaBudgetAlarm(CollectorMessage msg) {
        log.info("[MQ][RECV] NAVER GFA BUDGET ALARM userId={}", msg.getUserId());
        try {
            naverGfaBudgetAlarmJob.collectForUserId(msg.getUserId());
        } catch (Exception e) {
            log.error("[MQ][ERROR] NAVER GFA BUDGET ALARM userId={} error={}", msg.getUserId(), e.getMessage(), e);
        }
    }

    @RabbitListener(queues = RabbitMQConfig.QUEUE_NAVER_GFA_CONV_TYPE, concurrency = "3")
    public void consumeNaverGfaConvType(CollectorMessage msg) {
        log.info("[MQ][RECV] NAVER GFA CONV TYPE userId={}", msg.getUserId());
        try {
            if (hasRange(msg)) {
                naverGfaConvTypeJob.collectRange(msg.getUserId(), msg.getFromDate(), msg.getToDate());
            } else {
                naverGfaConvTypeJob.collectForUserId(msg.getUserId());
            }
        } catch (Exception e) {
            log.error("[MQ][ERROR] NAVER GFA CONV TYPE userId={} error={}", msg.getUserId(), e.getMessage(), e);
        }
    }

    // ── Kakao SA ──────────────────────────────────────────────────────────────

    @RabbitListener(queues = RabbitMQConfig.QUEUE_KAKAO_SA_AD_DETAIL, concurrency = "3")
    public void consumeKakaoSaAdDetail(CollectorMessage msg) {
        log.info("[MQ][RECV] KAKAO SA AD DETAIL userId={}", msg.getUserId());
        try {
            kakaoSaAdDetailJob.collectForUserId(msg.getUserId());
        } catch (Exception e) {
            log.error("[MQ][ERROR] KAKAO SA AD DETAIL userId={} error={}", msg.getUserId(), e.getMessage(), e);
        }
    }

    @RabbitListener(queues = RabbitMQConfig.QUEUE_KAKAO_SA_MASTER, concurrency = "3")
    public void consumeKakaoSaMaster(CollectorMessage msg) {
        log.info("[MQ][RECV] KAKAO SA MASTER userId={}", msg.getUserId());
        try {
            kakaoSaMasterJob.collectForUserId(msg.getUserId());
        } catch (Exception e) {
            log.error("[MQ][ERROR] KAKAO SA MASTER userId={} error={}", msg.getUserId(), e.getMessage(), e);
        }
    }

    @RabbitListener(queues = RabbitMQConfig.QUEUE_KAKAO_SA_CAMPAIGN_DAILY, concurrency = "3")
    public void consumeKakaoSaCampaignDaily(CollectorMessage msg) {
        log.info("[MQ][RECV] KAKAO SA CAMPAIGN DAILY userId={}", msg.getUserId());
        try {
            if (hasRange(msg)) {
                kakaoSaCampaignDayJob.collectRange(msg.getUserId(), msg.getFromDate(), msg.getToDate());
            } else {
                kakaoSaCampaignDayJob.collectForUserId(msg.getUserId());
            }
        } catch (Exception e) {
            log.error("[MQ][ERROR] KAKAO SA CAMPAIGN DAILY userId={} error={}", msg.getUserId(), e.getMessage(), e);
        }
    }

    @RabbitListener(queues = RabbitMQConfig.QUEUE_KAKAO_SA_CAMPAIGN_HOUR, concurrency = "3")
    public void consumeKakaoSaCampaignHour(CollectorMessage msg) {
        log.info("[MQ][RECV] KAKAO SA CAMPAIGN HOUR userId={}", msg.getUserId());
        try {
            if (hasRange(msg)) {
                kakaoSaCampaignHourJob.collectRange(msg.getUserId(), msg.getFromDate(), msg.getToDate());
            } else {
                kakaoSaCampaignHourJob.collectForUserId(msg.getUserId());
            }
        } catch (Exception e) {
            log.error("[MQ][ERROR] KAKAO SA CAMPAIGN HOUR userId={} error={}", msg.getUserId(), e.getMessage(), e);
        }
    }

    @RabbitListener(queues = RabbitMQConfig.QUEUE_KAKAO_SA_ADGROUP_DAILY, concurrency = "3")
    public void consumeKakaoSaAdGroupDaily(CollectorMessage msg) {
        log.info("[MQ][RECV] KAKAO SA ADGROUP DAILY userId={}", msg.getUserId());
        try {
            if (hasRange(msg)) {
                kakaoSaAdGroupDayJob.collectRange(msg.getUserId(), msg.getFromDate(), msg.getToDate());
            } else {
                kakaoSaAdGroupDayJob.collectForUserId(msg.getUserId());
            }
        } catch (Exception e) {
            log.error("[MQ][ERROR] KAKAO SA ADGROUP DAILY userId={} error={}", msg.getUserId(), e.getMessage(), e);
        }
    }

    @RabbitListener(queues = RabbitMQConfig.QUEUE_KAKAO_SA_KEYWORD_DAILY, concurrency = "3")
    public void consumeKakaoSaKeywordDaily(CollectorMessage msg) {
        log.info("[MQ][RECV] KAKAO SA KEYWORD DAILY userId={}", msg.getUserId());
        try {
            if (hasRange(msg)) {
                kakaoSaKeywordDayJob.collectRange(msg.getUserId(), msg.getFromDate(), msg.getToDate());
            } else {
                kakaoSaKeywordDayJob.collectForUserId(msg.getUserId());
            }
        } catch (Exception e) {
            log.error("[MQ][ERROR] KAKAO SA KEYWORD DAILY userId={} error={}", msg.getUserId(), e.getMessage(), e);
        }
    }

    @RabbitListener(queues = RabbitMQConfig.QUEUE_KAKAO_SA_AD_DAILY, concurrency = "3")
    public void consumeKakaoSaAdDaily(CollectorMessage msg) {
        log.info("[MQ][RECV] KAKAO SA AD DAILY userId={}", msg.getUserId());
        try {
            if (hasRange(msg)) {
                kakaoSaAdDayJob.collectRange(msg.getUserId(), msg.getFromDate(), msg.getToDate());
            } else {
                kakaoSaAdDayJob.collectForUserId(msg.getUserId());
            }
        } catch (Exception e) {
            log.error("[MQ][ERROR] KAKAO SA AD DAILY userId={} error={}", msg.getUserId(), e.getMessage(), e);
        }
    }

    @RabbitListener(queues = RabbitMQConfig.QUEUE_KAKAO_SA_BUDGET_ALARM, concurrency = "3")
    public void consumeKakaoSaBudgetAlarm(CollectorMessage msg) {
        log.info("[MQ][RECV] KAKAO SA BUDGET ALARM userId={}", msg.getUserId());
        try {
            kakaoSaBudgetAlarmJob.collectForUserId(msg.getUserId());
        } catch (Exception e) {
            log.error("[MQ][ERROR] KAKAO SA BUDGET ALARM userId={} error={}", msg.getUserId(), e.getMessage(), e);
        }
    }

    // ── Kakao MO ──────────────────────────────────────────────────────────────

    @RabbitListener(queues = RabbitMQConfig.QUEUE_KAKAO_MO_AD_DETAIL, concurrency = "3")
    public void consumeKakaoMoAdDetail(CollectorMessage msg) {
        log.info("[MQ][RECV] KAKAO MO AD DETAIL userId={}", msg.getUserId());
        try {
            kakaoMoAdDetailJob.collectForUserId(msg.getUserId());
        } catch (Exception e) {
            log.error("[MQ][ERROR] KAKAO MO AD DETAIL userId={} error={}", msg.getUserId(), e.getMessage(), e);
        }
    }

    @RabbitListener(queues = RabbitMQConfig.QUEUE_KAKAO_MO_MASTER, concurrency = "3")
    public void consumeKakaoMoMaster(CollectorMessage msg) {
        log.info("[MQ][RECV] KAKAO MO MASTER userId={}", msg.getUserId());
        try {
            kakaoMoMasterJob.collectForUserId(msg.getUserId());
        } catch (Exception e) {
            log.error("[MQ][ERROR] KAKAO MO MASTER userId={} error={}", msg.getUserId(), e.getMessage(), e);
        }
    }

    @RabbitListener(queues = RabbitMQConfig.QUEUE_KAKAO_MO_CAMPAIGN_DAILY, concurrency = "3")
    public void consumeKakaoMoCampaignDaily(CollectorMessage msg) {
        log.info("[MQ][RECV] KAKAO MO CAMPAIGN DAILY userId={}", msg.getUserId());
        try {
            if (hasRange(msg)) {
                kakaoMoCampaignDayJob.collectRange(msg.getUserId(), msg.getFromDate(), msg.getToDate());
            } else {
                kakaoMoCampaignDayJob.collectForUserId(msg.getUserId());
            }
        } catch (Exception e) {
            log.error("[MQ][ERROR] KAKAO MO CAMPAIGN DAILY userId={} error={}", msg.getUserId(), e.getMessage(), e);
        }
    }

    @RabbitListener(queues = RabbitMQConfig.QUEUE_KAKAO_MO_CAMPAIGN_HOUR, concurrency = "3")
    public void consumeKakaoMoCampaignHour(CollectorMessage msg) {
        log.info("[MQ][RECV] KAKAO MO CAMPAIGN HOUR userId={}", msg.getUserId());
        try {
            if (hasRange(msg)) {
                kakaoMoCampaignHourJob.collectRange(msg.getUserId(), msg.getFromDate(), msg.getToDate());
            } else {
                kakaoMoCampaignHourJob.collectForUserId(msg.getUserId());
            }
        } catch (Exception e) {
            log.error("[MQ][ERROR] KAKAO MO CAMPAIGN HOUR userId={} error={}", msg.getUserId(), e.getMessage(), e);
        }
    }

    @RabbitListener(queues = RabbitMQConfig.QUEUE_KAKAO_MO_ADGROUP_DAILY, concurrency = "3")
    public void consumeKakaoMoAdGroupDaily(CollectorMessage msg) {
        log.info("[MQ][RECV] KAKAO MO ADGROUP DAILY userId={}", msg.getUserId());
        try {
            if (hasRange(msg)) {
                kakaoMoAdGroupDayJob.collectRange(msg.getUserId(), msg.getFromDate(), msg.getToDate());
            } else {
                kakaoMoAdGroupDayJob.collectForUserId(msg.getUserId());
            }
        } catch (Exception e) {
            log.error("[MQ][ERROR] KAKAO MO ADGROUP DAILY userId={} error={}", msg.getUserId(), e.getMessage(), e);
        }
    }

    @RabbitListener(queues = RabbitMQConfig.QUEUE_KAKAO_MO_AD_DAILY, concurrency = "3")
    public void consumeKakaoMoAdDaily(CollectorMessage msg) {
        log.info("[MQ][RECV] KAKAO MO AD DAILY userId={}", msg.getUserId());
        try {
            if (hasRange(msg)) {
                kakaoMoAdDayJob.collectRange(msg.getUserId(), msg.getFromDate(), msg.getToDate());
            } else {
                kakaoMoAdDayJob.collectForUserId(msg.getUserId());
            }
        } catch (Exception e) {
            log.error("[MQ][ERROR] KAKAO MO AD DAILY userId={} error={}", msg.getUserId(), e.getMessage(), e);
        }
    }

    @RabbitListener(queues = RabbitMQConfig.QUEUE_KAKAO_MO_BUDGET_ALARM, concurrency = "3")
    public void consumeKakaoMoBudgetAlarm(CollectorMessage msg) {
        log.info("[MQ][RECV] KAKAO MO BUDGET ALARM userId={}", msg.getUserId());
        try {
            kakaoMoBudgetAlarmJob.collectForUserId(msg.getUserId());
        } catch (Exception e) {
            log.error("[MQ][ERROR] KAKAO MO BUDGET ALARM userId={} error={}", msg.getUserId(), e.getMessage(), e);
        }
    }

    // ── Google ──────────────────────────────────────────────────────────────────

    @RabbitListener(queues = RabbitMQConfig.QUEUE_GOOGLE_MASTER, concurrency = "3")
    public void consumeGoogleMaster(CollectorMessage msg) {
        log.info("[MQ][RECV] GOOGLE MASTER userId={}", msg.getUserId());
        try {
            googleMasterJob.collectForUserId(msg.getUserId());
        } catch (Exception e) {
            log.error("[MQ][ERROR] GOOGLE MASTER userId={} error={}", msg.getUserId(), e.getMessage(), e);
        }
    }

    @RabbitListener(queues = RabbitMQConfig.QUEUE_GOOGLE_CAMPAIGN_DAILY, concurrency = "3")
    public void consumeGoogleCampaignDaily(CollectorMessage msg) {
        log.info("[MQ][RECV] GOOGLE CAMPAIGN DAILY userId={}", msg.getUserId());
        try {
            if (hasRange(msg)) {
                googleCampaignDayJob.collectRange(msg.getUserId(), msg.getFromDate(), msg.getToDate());
            } else {
                googleCampaignDayJob.collectForUserId(msg.getUserId());
            }
        } catch (Exception e) {
            log.error("[MQ][ERROR] GOOGLE CAMPAIGN DAILY userId={} error={}", msg.getUserId(), e.getMessage(), e);
        }
    }

    @RabbitListener(queues = RabbitMQConfig.QUEUE_GOOGLE_CAMPAIGN_HOUR, concurrency = "3")
    public void consumeGoogleCampaignHour(CollectorMessage msg) {
        log.info("[MQ][RECV] GOOGLE CAMPAIGN HOUR userId={}", msg.getUserId());
        try {
            if (hasRange(msg)) {
                googleCampaignHourJob.collectRange(msg.getUserId(), msg.getFromDate(), msg.getToDate());
            } else {
                googleCampaignHourJob.collectForUserId(msg.getUserId());
            }
        } catch (Exception e) {
            log.error("[MQ][ERROR] GOOGLE CAMPAIGN HOUR userId={} error={}", msg.getUserId(), e.getMessage(), e);
        }
    }

    @RabbitListener(queues = RabbitMQConfig.QUEUE_GOOGLE_ADGROUP_DAILY, concurrency = "3")
    public void consumeGoogleAdGroupDaily(CollectorMessage msg) {
        log.info("[MQ][RECV] GOOGLE ADGROUP DAILY userId={}", msg.getUserId());
        try {
            if (hasRange(msg)) {
                googleAdGroupDayJob.collectRange(msg.getUserId(), msg.getFromDate(), msg.getToDate());
            } else {
                googleAdGroupDayJob.collectForUserId(msg.getUserId());
            }
        } catch (Exception e) {
            log.error("[MQ][ERROR] GOOGLE ADGROUP DAILY userId={} error={}", msg.getUserId(), e.getMessage(), e);
        }
    }

    @RabbitListener(queues = RabbitMQConfig.QUEUE_GOOGLE_AD_DAILY, concurrency = "3")
    public void consumeGoogleAdDaily(CollectorMessage msg) {
        log.info("[MQ][RECV] GOOGLE AD DAILY userId={}", msg.getUserId());
        try {
            if (hasRange(msg)) {
                googleAdDayJob.collectRange(msg.getUserId(), msg.getFromDate(), msg.getToDate());
            } else {
                googleAdDayJob.collectForUserId(msg.getUserId());
            }
        } catch (Exception e) {
            log.error("[MQ][ERROR] GOOGLE AD DAILY userId={} error={}", msg.getUserId(), e.getMessage(), e);
        }
    }

    @RabbitListener(queues = RabbitMQConfig.QUEUE_GOOGLE_KEYWORD_DAILY, concurrency = "3")
    public void consumeGoogleKeywordDaily(CollectorMessage msg) {
        log.info("[MQ][RECV] GOOGLE KEYWORD DAILY userId={}", msg.getUserId());
        try {
            if (hasRange(msg)) {
                googleKeywordDayJob.collectRange(msg.getUserId(), msg.getFromDate(), msg.getToDate());
            } else {
                googleKeywordDayJob.collectForUserId(msg.getUserId());
            }
        } catch (Exception e) {
            log.error("[MQ][ERROR] GOOGLE KEYWORD DAILY userId={} error={}", msg.getUserId(), e.getMessage(), e);
        }
    }

    private boolean hasRange(CollectorMessage msg) {
        return msg.getFromDate() != null && !msg.getFromDate().isEmpty();
    }
}
