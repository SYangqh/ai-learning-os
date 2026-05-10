package com.learningos.infrastructure.crypto;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 多密钥环（支持密钥轮换）。
 * <p>
 * 环境变量格式：{@code APP_ENCRYPTION_KEYS}，逗号分隔的 {@code id:base64(32bytes)} 对。
 * 列表中第一个条目为「当前加密密钥」，其余仅用于解密旧密文。
 * </p>
 * <pre>
 * APP_ENCRYPTION_KEYS=v2:&lt;base64&gt;,v1:&lt;base64&gt;
 * </pre>
 */
@Component
public class KeyRing {

    /** 保持插入顺序：第一个为当前活跃密钥 */
    private final Map<String, SecretKey> keys = new LinkedHashMap<>();
    private final String currentKeyId;

    public KeyRing(@Value("${app.encryption.keys}") String rawKeysConfig) {
        if (rawKeysConfig == null || rawKeysConfig.isBlank()) {
            throw new IllegalStateException("app.encryption.keys must not be empty");
        }

        String firstId = null;
        for (String entry : rawKeysConfig.split(",")) {
            entry = entry.trim();
            if (entry.isEmpty()) continue;

            int colon = entry.indexOf(':');
            if (colon < 1) {
                throw new IllegalStateException(
                    "Invalid key entry (expected 'id:base64'): " + entry);
            }

            String keyId = entry.substring(0, colon).trim();
            byte[] keyBytes = Base64.getDecoder().decode(entry.substring(colon + 1).trim());
            if (keyBytes.length != 32) {
                throw new IllegalStateException(
                    "Encryption key '" + keyId + "' must be exactly 32 bytes (AES-256), got " + keyBytes.length);
            }

            keys.put(keyId, new SecretKeySpec(keyBytes, "AES"));
            if (firstId == null) firstId = keyId;
        }

        if (keys.isEmpty()) {
            throw new IllegalStateException("No valid encryption keys found in app.encryption.keys");
        }
        this.currentKeyId = firstId;
    }

    /** 返回当前用于加密的密钥 ID */
    public String currentKeyId() {
        return currentKeyId;
    }

    /** 返回当前用于加密的密钥 */
    public SecretKey currentKey() {
        return keys.get(currentKeyId);
    }

    /** 按 ID 查找密钥（用于解密历史密文） */
    public SecretKey getKey(String keyId) {
        SecretKey key = keys.get(keyId);
        if (key == null) {
            throw new IllegalArgumentException("Unknown encryption key id: " + keyId);
        }
        return key;
    }
}
