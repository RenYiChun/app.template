package com.lrenyi.template.core.coder;

import org.springframework.security.crypto.password.PasswordEncoder;

public interface TemplateDataCoder extends PasswordEncoder {
    String DEFAULT_ENCODER_KEY = "default";
    
    String type();
    
    String decode(String encodedPassword);
}
