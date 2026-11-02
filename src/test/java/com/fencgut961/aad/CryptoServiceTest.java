package com.fencgut961.aad;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class CryptoServiceTest {

    private final CryptoService crypto = new CryptoService("AadTexSecret2026");

    @Test
    void cifrarYDescifrarDevuelveElOriginal() throws Exception {
        // incluye caracteres chinos para comprobar que UTF-8 no se rompe
        byte[] original = "ZARA_CH_001,Zara \u4e0a\u6d77,28900.00".getBytes(StandardCharsets.UTF_8);

        byte[] cifrado = crypto.encrypt(original);

        assertFalse(Arrays.equals(original, cifrado));
        assertArrayEquals(original, crypto.decrypt(cifrado));
    }
}