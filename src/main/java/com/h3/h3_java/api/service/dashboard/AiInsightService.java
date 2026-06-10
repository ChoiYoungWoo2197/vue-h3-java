package com.h3.h3_java.api.service.dashboard;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Service
@RequiredArgsConstructor
public class AiInsightService {

    @Value("${openai.api-key}")
    private String apiKey;

    @Value("${openai.model:gpt-4.1-mini}")
    private String model;

    @Value("${openai.temperature:0.2}")
    private double temperature;

    private static final String OPENAI_URL = "https://api.openai.com/v1/chat/completions";

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String INSIGHT_SYSTEM =
        "너는 퍼포먼스 마케팅 데이터 분석가다. " +
        "반드시 아래의 JSON 스키마를 '그대로' 지켜서 출력해야 한다. " +
        "출력은 JSON 객체 1개만 허용된다. 다른 텍스트/설명/마크다운/코드펜스(```)/주석을 절대 출력하지 마라. " +
        "키 이름 변경/추가/삭제 금지. 타입 변경 금지. " +
        "값을 만들 때는 입력 데이터(수치/패턴) 기반으로만 작성하고, 근거 없는 추측/과장 금지. " +
        "데이터가 부족하면 해당 항목은 빈 문자열/빈 배열/빈 객체로 채워라.\n\n" +
        "=== 출력 JSON 스키마(반드시 동일) ===\n" +
        "{\n" +
        "  \"schema_version\": \"1.0\",\n" +
        "  \"badge\": { \"type\": \"ok|warn|off\", \"text\": \"string\" },\n" +
        "  \"summary_text\": \"string\",\n" +
        "  \"highlights\": [ { \"k\": \"string\", \"v\": \"string\", \"dir\": \"up|down|flat\" } ],\n" +
        "  \"quick_questions\": [ \"string\" ],\n" +
        "  \"changes\": [ { \"text\": \"string\", \"evidence\": \"string\", \"focus\": { \"type\": \"daily|metric|media\", \"range\": [0,0], \"key\": \"string\", \"media\": \"string\" } } ],\n" +
        "  \"hypotheses\": [ { \"text\": \"string\", \"evidence\": \"string\" } ],\n" +
        "  \"actions\": { \"today\": [ \"string\" ], \"this_week\": [ \"string\" ], \"next_week\": [ \"string\" ] },\n" +
        "  \"disclaimer\": \"string\"\n" +
        "}\n\n" +
        "=== 필수 규칙 ===\n" +
        "0) summary_text 작성 규칙 (반드시 준수):\n" +
        "   - 전체 2문장 이내로 작성. 3문장 이상 절대 금지.\n" +
        "   - 첫 문장: 전체 성과를 결론 한 줄로 요약. 가장 중요한 트렌드를 한 마디로.\n" +
        "   - 두 번째 문장: 핵심 지표 수치 증감 나열 (볼륨 지표 먼저, 효율 지표 뒤).\n" +
        "   - 조회 날짜/기간/광고주명을 summary_text에 절대 포함하지 마라.\n" +
        "   - 지표 수치는 pct_str(예: +13.1%) 형식을 그대로 사용.\n" +
        "1) highlights는 최대 6개. k는 반드시 다음 KPI key 중 하나만 사용:\n" +
        " - cst|im|clk|cv|cr|ctr|cpc|cpa|cvr|roas\n" +
        " - v는 '+13.1%' 같은 표기, dir은 up/down/flat.\n" +
        "2) quick_questions는 최대 6개.\n" +
        "3) changes는 최대 6개. evidence에는 반드시 수치/퍼센트 같은 근거를 포함.\n" +
        "4) actions는 today/this_week/next_week 각각 최대 4개.\n" +
        "5) badge.type 기준: 입력 데이터 충분하면 ok, 일부 누락/표본 적으면 warn, 핵심 데이터 부재면 off.\n" +
        "6) focus 객체는 항상 존재해야 한다. 필요 없는 필드는 빈값으로 유지.\n" +
        "7) 절대 추가 키를 만들지 마라.\n";

    private static final String FOLLOWUP_SYSTEM =
        "너는 퍼포먼스 마케팅 데이터 분석가다. " +
        "사용자가 특정 질문을 선택하면 입력 데이터(JSON)를 기반으로 원인 분석과 개선 방향을 설명한다. " +
        "출력은 반드시 JSON 객체 하나만 출력한다. 다른 설명이나 텍스트는 절대 출력하지 마라.\n\n" +
        "=== 출력 JSON 스키마 ===\n" +
        "{\n" +
        "  \"question\": \"string\",\n" +
        "  \"answer\": \"string\",\n" +
        "  \"reasons\": [\"string\"],\n" +
        "  \"evidence\": [\"string\"],\n" +
        "  \"actions\": [\"string\"],\n" +
        "  \"disclaimer\": \"string\"\n" +
        "}\n\n" +
        "규칙:\n" +
        "1) answer는 질문에 대한 직접적인 설명\n" +
        "2) reasons는 주요 원인 bullet\n" +
        "3) evidence는 숫자 근거 포함\n" +
        "4) actions는 개선 액션\n" +
        "5) 데이터 근거 없이 추측하지 마라.\n";

    public Map<String, Object> analyze(String userid, String payloadJson, boolean debug) {
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            Object payloadObj = objectMapper.readValue(payloadJson, Object.class);
            String userContent = "입력 데이터(JSON)는 아래와 같다. 이 데이터만 근거로 위 스키마에 맞춰 채워라.\n" +
                "- 출력은 JSON 객체 1개만.\n- 스키마 키/타입 변경 금지.\n\nINPUT_JSON:\n" +
                objectMapper.writeValueAsString(payloadObj);

            String content = callOpenAi(INSIGHT_SYSTEM, userContent);

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("userid", userid);
            data.put("insight_md", content);
            if (debug) {
                Map<String, Object> debugInfo = new LinkedHashMap<>();
                debugInfo.put("payload_bytes", payloadJson.length());
                data.put("debug", debugInfo);
            } else {
                data.put("debug", null);
            }
            result.put("result", "success");
            result.put("status", "200");
            result.put("data", data);
        } catch (Exception e) {
            result.put("result", "fail");
            result.put("status", "500");
            result.put("data", Map.of("message", e.getMessage()));
        }
        return result;
    }

    public Map<String, Object> followup(String userid, String payloadJson, String question, boolean debug) {
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            Object payloadObj = objectMapper.readValue(payloadJson, Object.class);
            String userContent = "사용자가 선택한 질문:\n" + question + "\n\n입력 데이터(JSON):\n" +
                objectMapper.writeValueAsString(payloadObj);

            String content = callOpenAi(FOLLOWUP_SYSTEM, userContent);

            Object decoded;
            try {
                decoded = objectMapper.readValue(content, Object.class);
            } catch (Exception ex) {
                Map<String, Object> fallback = new LinkedHashMap<>();
                fallback.put("question", question);
                fallback.put("answer", "AI 응답 파싱에 실패했습니다.");
                fallback.put("reasons", List.of());
                fallback.put("evidence", List.of());
                fallback.put("actions", List.of());
                fallback.put("disclaimer", "일시적인 AI 응답 오류");
                decoded = fallback;
            }

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("userid", userid);
            data.put("followup", decoded);
            data.put("debug", debug ? Map.of("payload_bytes", payloadJson.length()) : null);

            result.put("result", "success");
            result.put("status", "200");
            result.put("data", data);
        } catch (Exception e) {
            result.put("result", "fail");
            result.put("status", "500");
            result.put("data", Map.of("message", e.getMessage()));
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private String callOpenAi(String system, String user) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("temperature", temperature);
        body.put("messages", List.of(
            Map.of("role", "system", "content", system),
            Map.of("role", "user", "content", user)
        ));

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        ResponseEntity<Map> response = restTemplate.postForEntity(OPENAI_URL, entity, Map.class);

        Map<String, Object> resBody = response.getBody();
        if (resBody == null) throw new RuntimeException("OpenAI 응답 없음");

        List<Map<String, Object>> choices = (List<Map<String, Object>>) resBody.get("choices");
        if (choices == null || choices.isEmpty()) throw new RuntimeException("choices 없음");

        Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
        return (String) message.get("content");
    }
}
