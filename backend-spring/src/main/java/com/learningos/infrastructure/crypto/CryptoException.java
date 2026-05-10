package com.learningos.infrastructure.crypto;

/**
 * 加解密操作异常（不可恢复，通常触发 HTTP 500）。
 */
public class CryptoException extends RuntimeException {
    public CryptoException(String message, Throwable cause) {
        super(message, cause);
    }
}
