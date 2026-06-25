package com.h3.h3_java.api.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class SendGridService {

    @Value("${sendgrid.api-key}")
    private String apiKey;

    @Value("${sendgrid.from-email}")
    private String fromEmail;

    @Value("${sendgrid.from-name}")
    private String fromName;

    private static final String SEND_URL = "https://api.sendgrid.com/v3/mail/send";

    public boolean send(String toEmail, String toName, String subject, String htmlContent) {
        try {
            Map<String, Object> body = Map.of(
                "personalizations", List.of(Map.of(
                    "to", List.of(Map.of("email", toEmail, "name", toName))
                )),
                "from",    Map.of("email", fromEmail, "name", fromName),
                "subject", subject,
                "content", List.of(Map.of("type", "text/html", "value", htmlContent))
            );

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            ResponseEntity<String> res = new RestTemplate().exchange(
                SEND_URL, HttpMethod.POST, new HttpEntity<>(body, headers), String.class);

            boolean ok = res.getStatusCode().value() == 202;
            if (!ok) log.warn("[SENDGRID] 발송 실패 status={} body={}", res.getStatusCode(), res.getBody());
            return ok;
        } catch (Exception e) {
            log.error("[SENDGRID] 발송 오류 to={} err={}", toEmail, e.getMessage());
            return false;
        }
    }

    public boolean sendWithPdfAttachment(String toEmail, String toName, String subject,
                                          String htmlBody, byte[] pdfBytes, String fileName) {
        try {
            String base64Pdf = Base64.getEncoder().encodeToString(pdfBytes);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("personalizations", List.of(Map.of(
                "to", List.of(Map.of("email", toEmail, "name", toName))
            )));
            body.put("from",    Map.of("email", fromEmail, "name", fromName));
            body.put("subject", subject);
            body.put("content", List.of(Map.of("type", "text/html", "value", htmlBody)));
            body.put("attachments", List.of(Map.of(
                "content",     base64Pdf,
                "type",        "application/pdf",
                "filename",    fileName,
                "disposition", "attachment"
            )));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            ResponseEntity<String> res = new RestTemplate().exchange(
                SEND_URL, HttpMethod.POST, new HttpEntity<>(body, headers), String.class);

            boolean ok = res.getStatusCode().value() == 202;
            if (!ok) log.warn("[SENDGRID] PDF 발송 실패 status={}", res.getStatusCode());
            return ok;
        } catch (Exception e) {
            log.error("[SENDGRID] PDF 발송 오류 to={} err={}", toEmail, e.getMessage());
            return false;
        }
    }

    public String buildAuthCodeHtml(String title, String bodyText, String code) {
        return "<div style='margin-top:120px;font-family:Arial,sans-serif;'>"
            + "<table width='740' border='0' align='center' style='margin:0 auto;border:1px solid #dbdbdb;'>"
            + "<tr><td style='padding:30px 50px;'>"
            + "<h2 style='font-size:29px;color:#333;border-bottom:1px solid #dfdfdf;padding-bottom:20px;'>" + title + "</h2>"
            + "<p style='font-size:12px;color:#333;line-height:1.8;'>안녕하세요.<br>희일커뮤니케이션 입니다.<br><br>"
            + bodyText + "<br></p>"
            + "<p style='font-size:14px;color:#333;'><strong>" + code + "</strong></p>"
            + "<p style='font-size:12px;color:#333;'>감사합니다.</p>"
            + "</td></tr>"
            + "<tr><td style='padding:10px 50px;font-size:11px;color:#999;border-top:1px solid #dfdfdf;'>이 메일은 발신 전용 메일입니다.</td></tr>"
            + "</table></div>";
    }
}
