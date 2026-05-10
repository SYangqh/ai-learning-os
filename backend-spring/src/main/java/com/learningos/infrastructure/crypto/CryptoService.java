package com.learningos.infrastructure.crypto;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-256-GCM 加解密服务。
 *
 * <p>加密结果由 {@link EncryptedPayload} 封装，持久化三个字段：
 * {@code enc_ciphertext}、{@code enc_iv}（12 bytes）、{@code enc_tag}（GCM tag 含在密文末尾，由 JCA 自动追加）。
 * 解密时按 {@code key_id} 从 {@link KeyRing} 取正确的主密钥。</p>
 *
 * <p>线程安全：每次加解密均通过 {@code Cipher.getInstance} 获取新实例。</p>
 */
@Service
@RequiredArgsConstructor
public class CryptoService {

    private static final String ALGORITHM     = "AES/GCM/NoPadding";
    private static final int    IV_LENGTH_BYTE = 12;
    private static final int    TAG_LENGTH_BIT = 128;

    private final KeyRing keyRing;
    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * 加密明文字符串（UTF-8），返回可持久化的载荷。
     */
    public EncryptedPayload encrypt(String plaintext) {
        try {
            byte[] iv = new byte[IV_LENGTH_BYTE];
            secureRandom.nextBytes(iv);

            SecretKey key = keyRing.currentKey();
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BIT, iv));

            // JCA 将 GCM tag 追加到密文末尾
            byte[] cipherTextWithTag = cipher.doFinal(plaintext.getBytes(java.nio.charset.StandardCharsets.UTF_8));

            // 单独提取末尾 16 字节 GCM tag
            int tagBytes = TAG_LENGTH_BIT / 8;
            byte[] tag = java.util.Arrays.copyOfRange(cipherTextWithTag,
                    cipherTextWithTag.length - tagBytes, cipherTextWithTag.length);

            return new EncryptedPayload(
                Base64.getEncoder().encodeToString(cipherTextWithTag),
                Base64.getEncoder().encodeToString(iv),
                keyRing.currentKeyId(),
                Base64.getEncoder().encodeToString(tag)
            );
        } catch (Exception e) {
            throw new CryptoException("Encryption failed", e);
        }
    }

    /**
     * 解密：按 {@code keyId} 从 KeyRing 取密钥，失败抛 {@link CryptoException}。
     */
    public String decrypt(EncryptedPayload payload) {
        try {
            SecretKey key = keyRing.getKey(payload.keyId());
            byte[] iv              = Base64.getDecoder().decode(payload.iv());
            byte[] cipherTextBytes = Base64.getDecoder().decode(payload.ciphertext());

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BIT, iv));

            byte[] plainBytes = cipher.doFinal(cipherTextBytes);
            return new String(plainBytes, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new CryptoException("Decryption failed", e);
        }
    }
}
