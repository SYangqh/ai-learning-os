package com.learningos.infrastructure.crypto;

/**
 * 加密载荷（不可变 Record）。
 *
 * @param ciphertext Base64 编码的密文（含 GCM tag）
 * @param iv         Base64 编码的 IV（12 bytes）
 * @param keyId      使用的主密钥 ID（用于解密时定位密钥）
 */
public record EncryptedPayload(String ciphertext, String iv, String keyId) {}
