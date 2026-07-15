package com.zn.agentarena.common.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * API Key加解密工具单元测试。
 *
 * @author zhangxin
 * @date 2026/07/12
 */
@DisplayName("API Key加解密工具单元测试")
class ApiKeyCipherTest {

    private final ApiKeyCipher cipher = new ApiKeyCipher("agent-arena-unit-test-secret-32chars");

    @Test
    @DisplayName("encrypt: 加密结果不应包含明文且能够正确解密")
    void testEncryptAndDecryptSuccess() {
        String plainText = "sk-test-sensitive-key";

        String encrypted = cipher.encrypt(plainText);

        assertNotNull(encrypted);
        assertNotEquals(plainText, encrypted);
        assertFalse(encrypted.contains(plainText));
        assertEquals(plainText, cipher.decrypt(encrypted));
    }

    @Test
    @DisplayName("encrypt: 相同明文应因随机IV产生不同密文")
    void testRandomIv() {
        assertNotEquals(cipher.encrypt("same-key"), cipher.encrypt("same-key"));
    }
}
