package com.learningos.infrastructure.crypto;

/**
 * 加密载荷（不可变 Record）。
 *
 * @param ciphertext Base64 编码的密文（含 GCM tag）
 * @param iv         Base64 编码的 IV（12 bytes）
 * @param keyId      使用的主密钥 ID（用于解密时定位密钥）
 * @param tag        Base64 编码的 GCM tag（16 bytes，与密文末尾重复存储，方便独立审计）
 */
public record EncryptedPayload(String ciphertext, String iv, String keyId, String tag) {
    /** 兼容性构造：tag 为空时使用 null（用于从老数据反序列化） */
    public EncryptedPayload(String ciphertext, String iv, String keyId) {
        this(ciphertext, iv, keyId, null);
    }
}
