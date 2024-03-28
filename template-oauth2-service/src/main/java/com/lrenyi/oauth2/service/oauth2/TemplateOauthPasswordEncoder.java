package com.lrenyi.oauth2.service.oauth2;

import com.lrenyi.template.core.coder.GlobalDataCoder;
import org.springframework.security.crypto.password.PasswordEncoder;

public class TemplateOauthPasswordEncoder implements PasswordEncoder {
    final GlobalDataCoder encoder;
    
    public TemplateOauthPasswordEncoder(String defaultEncoderKey, GlobalDataCoder globalDataCoder) {
        globalDataCoder.setDefaultPasswordEncoderForMatches(defaultEncoderKey);
        this.encoder = globalDataCoder;
    }
    
    @Override
    public String encode(CharSequence rawPassword) {
        return encoder.encode(rawPassword);
    }
    
    @Override
    public boolean matches(CharSequence rawPassword, String encodedPassword) {
        return encoder.matches(rawPassword, encodedPassword);
    }
}
