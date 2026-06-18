package com.h3.h3_java.api.controller;

import com.h3.h3_java.api.service.user.CampaignService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/v1/h3/app/user")
@RequiredArgsConstructor
public class CampaignController {

    private final CampaignService campaignService;

    /**
     * 매체별 캠페인 목록 조회 (캠페인 필터 모달용)
     * media: naver_sa / naver_da / kakao_sa / kakao_da / google_ads
     */
    @GetMapping("/campaigns")
    public Map<String, Object> campaigns(
            @RequestParam String userid,
            @RequestParam String media) {

        if (userid == null || userid.isBlank()) {
            return Map.of("result", "fail", "status", "400",
                    "data", Map.of("message", "userid가 없습니다."));
        }
        return campaignService.getCampaigns(userid, media);
    }
}
