package com.lrenyi.oauth2.service.oauth2.token;

import java.util.UUID;
import org.springframework.security.crypto.keygen.StringKeyGenerator;

public class UuidKeyGenerator implements StringKeyGenerator {
    @Override
    public String generateKey() {
        return UUID.randomUUID().toString().toLowerCase();
    }
}
