package com.lrenyi.template.core.coder;

import java.util.Map;
import org.springframework.core.env.EnumerablePropertySource;

public class ConfigDecryption {
    protected static final String DECRYPTED_PROPERTY_SOURCE_NAME = "decryptedProperties";
    GlobalDataCoder encoder = new GlobalDataCoder();
    
    protected void decryptionCommon(EnumerablePropertySource<?> enumerablePropertySource,
            Map<String, Object> decryptedProperties) {
        for (String key : enumerablePropertySource.getPropertyNames()) {
            Object rawValue = enumerablePropertySource.getProperty(key);
            if (!(rawValue instanceof String encryptedValue)) {
                continue;
            }
            if (encryptedValue.startsWith("aENC(") && encryptedValue.endsWith(")")) {
                String decryptedValue = decryptValue(encryptedValue);
                decryptedProperties.put(key, decryptedValue);
            }
        }
    }
    
    private String decryptValue(String encryptedValue) {
        String ciphertext = encryptedValue.substring(5, encryptedValue.length() - 1);
        return encoder.decode(ciphertext);
    }
}
