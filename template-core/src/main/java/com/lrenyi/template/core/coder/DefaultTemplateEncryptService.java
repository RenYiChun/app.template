package com.lrenyi.template.core.coder;

import com.lrenyi.template.core.TemplateConfigProperties;
import com.lrenyi.template.core.util.StringUtils;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.ServiceLoader;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class DefaultTemplateEncryptService implements TemplateEncryptService, InitializingBean {
    private static final Map<String, PasswordEncoder> ALL_ENCODER = new HashMap<>();
    private TemplateConfigProperties templateConfigProperties;
    
    public DefaultTemplateEncryptService() {}
    
    @Autowired
    public DefaultTemplateEncryptService(TemplateConfigProperties templateConfigProperties) {
        this.templateConfigProperties = templateConfigProperties;
    }
    
    static {
        ServiceLoader<TemplateEncryptService> load = ServiceLoader.load(TemplateEncryptService.class);
        for (TemplateEncryptService encoder : load) {
            ALL_ENCODER.put(encoder.type(), encoder);
        }
        PasswordEncoder defaultPasswordEncoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();
        if (defaultPasswordEncoder instanceof DelegatingPasswordEncoder delegatingPasswordEncoder) {
            try {
                Field encoder = DelegatingPasswordEncoder.class.getDeclaredField("idToPasswordEncoder");
                encoder.setAccessible(true);
                Object mapValue = encoder.get(delegatingPasswordEncoder);
                if (mapValue instanceof Map<?, ?> map) {
                    map.forEach((key, value) -> {
                        if (value instanceof PasswordEncoder passwordEncoder) {
                            ALL_ENCODER.put(String.valueOf(key), passwordEncoder);
                        }
                    });
                }
            } catch (NoSuchFieldException | IllegalAccessException ignore) {}
        }
    }
    
    private String defaultPasswordEncoderKey;
    private PasswordEncoder defaultPasswordEncoderForMatches = new UnmappedIdPasswordEncoder();
    
    public void setDefaultPasswordEncoderForMatches(String encoderKey) {
        if (!StringUtils.hasLength(encoderKey)) {
            throw new IllegalArgumentException("defaultPasswordEncoderForMatches cannot be null");
        }
        this.defaultPasswordEncoderKey = encoderKey;
        this.defaultPasswordEncoderForMatches = ALL_ENCODER.get(encoderKey);
    }
    
    @Override
    public String type() {
        return "CoderFactory";
    }
    
    @Override
    public String decode(String encodedPassword) {
        KeyPassword keyPassword = encodedBySelf(encodedPassword);
        if (keyPassword == null) {
            throw new IllegalArgumentException("can not decode data of the encoder");
        }
        PasswordEncoder encoder = ALL_ENCODER.get(keyPassword.encoderKey);
        if (encoder instanceof TemplateEncryptService templateDataCoder) {
            return templateDataCoder.decode(keyPassword.password);
        }
        throw new IllegalArgumentException("not find decoder: " + keyPassword.encoderKey);
    }
    
    private static KeyPassword encodedBySelf(String encodedPassword) {
        if (!encodedPassword.startsWith("{") && !encodedPassword.contains("}")) {
            return null;
        }
        String extractId = extractId(encodedPassword);
        String password = extractEncodedPassword(encodedPassword);
        return new KeyPassword(extractId, password);
    }
    
    @Override
    public String encode(CharSequence rawPassword) {
        if (rawPassword == null) {
            return null;
        }
        if (rawPassword.toString().startsWith("{noop}")) {
            return rawPassword.toString();
        }
        if (defaultPasswordEncoderForMatches == null) {
            throw new UnsupportedOperationException("encode is not supported");
        }
        String encode = defaultPasswordEncoderForMatches.encode(rawPassword);
        return "{" + defaultPasswordEncoderKey + "}" + encode;
    }
    
    @Override
    public boolean matches(CharSequence rawPassword, String encodedPassword) {
        KeyPassword keyPassword = encodedBySelf(encodedPassword);
        if (keyPassword == null) {
            String keyId = extractId(encodedPassword);
            PasswordEncoder encoder = ALL_ENCODER.get(keyId);
            if (encoder == null) {
                log.warn("no {} type codec found, use default coder: {}",
                         keyId,
                         defaultPasswordEncoderForMatches.getClass().getName()
                );
                return this.defaultPasswordEncoderForMatches.matches(rawPassword, encodedPassword);
            }
            String password = extractEncodedPassword(encodedPassword);
            return encoder.matches(rawPassword, password);
        } else {
            String encoderKey = keyPassword.getEncoderKey();
            PasswordEncoder encoder = ALL_ENCODER.get(encoderKey);
            if (encoder == null) {
                log.error("no {} type codec found", encoderKey);
                return this.defaultPasswordEncoderForMatches.matches(rawPassword, encodedPassword);
            }
            return encoder.matches(rawPassword, keyPassword.password);
        }
    }
    
    private static String extractId(String prefixEncodedPassword) {
        if (prefixEncodedPassword == null) {
            return null;
        }
        int start = prefixEncodedPassword.indexOf("{");
        if (start != 0) {
            return null;
        }
        int end = prefixEncodedPassword.indexOf("}", start);
        if (end < 0) {
            return null;
        }
        return prefixEncodedPassword.substring(start + 1, end);
    }
    
    private static String extractEncodedPassword(String prefixEncodedPassword) {
        int start = prefixEncodedPassword.indexOf("}");
        return prefixEncodedPassword.substring(start + 1);
    }
    
    @Override
    public void afterPropertiesSet() {
        String defaultEncryptKey = "default";
        if (templateConfigProperties != null) {
            defaultEncryptKey = templateConfigProperties.getSecurity().getSecurityKey();
        }
        setDefaultPasswordEncoderForMatches(defaultEncryptKey);
    }
    
    @Data
    @AllArgsConstructor
    static class KeyPassword {
        private String encoderKey;
        private String password;
    }
    
    private static class UnmappedIdPasswordEncoder implements TemplateEncryptService {
        
        @Override
        public String encode(CharSequence rawPassword) {
            throw new UnsupportedOperationException("encode is not supported");
        }
        
        @Override
        public boolean matches(CharSequence rawPassword, String prefixEncodedPassword) {
            KeyPassword keyPassword = encodedBySelf(prefixEncodedPassword);
            String id = null;
            if (keyPassword != null) {
                id = keyPassword.encoderKey;
            }
            throw new IllegalArgumentException("There is no PasswordEncoder mapped for the id: \"" + id + "\"");
        }
        
        @Override
        public String type() {
            return "Unmapped";
        }
        
        @Override
        public String decode(String encodedPassword) {
            throw new UnsupportedOperationException("encode is not supported");
        }
    }
}
