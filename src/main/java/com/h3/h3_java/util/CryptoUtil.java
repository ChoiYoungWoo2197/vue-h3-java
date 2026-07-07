package com.h3.h3_java.util;

import lombok.extern.slf4j.Slf4j;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

@Slf4j
public class CryptoUtil {

    private static final String ALGORITHM      = "AES";
    private static final String TRANSFORMATION = "AES/ECB/PKCS5Padding";
    private static final String ENC_KEY        = "heeilheeil2025Ky"; // 16-byte = 128-bit AES

    public static String encrypt(String data) {
        if (data == null) return null;
        try {
            SecretKey secretKey = new SecretKeySpec(ENC_KEY.getBytes(), ALGORITHM);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey);
            return Base64.getEncoder().encodeToString(cipher.doFinal(data.getBytes()));
        } catch (Exception e) {
            log.error("[CryptoUtil] encrypt error: {}", e.getMessage());
            return data;
        }
    }

    public static String decrypt(String encryptedData) {
        if (encryptedData == null) return null;
        try {
            SecretKey secretKey = new SecretKeySpec(ENC_KEY.getBytes(), ALGORITHM);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, secretKey);
            return new String(cipher.doFinal(Base64.getDecoder().decode(encryptedData)));
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 복호화 실패 시 원본 반환 — 평문 → 암호화 마이그레이션 기간 호환용
     */
    public static String safeDecrypt(String value) {
        if (value == null) return null;
        String decrypted = decrypt(value);
        return decrypted != null ? decrypted : value;
    }
}
