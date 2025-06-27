package com.lrenyi.template.core.coder.coder;

import com.lrenyi.template.core.coder.TemplateEncryptService;
import com.lrenyi.template.core.util.RsaUtils;
import org.kohsuke.MetaInfServices;

@MetaInfServices
public class TemplateRsa2048Coder implements TemplateEncryptService {
    
    @Override
    public String type() {
        return "RSA2048";
    }
    
    @Override
    public String decode(String encodedPassword) {
        return RsaUtils.decrypt(encodedPassword);
    }
    
    @Override
    public String encode(CharSequence rawPassword) {
        return RsaUtils.encryption(rawPassword.toString());
    }
    
    @Override
    public boolean matches(CharSequence rawPassword, String encodedPassword) {
        String encryption = RsaUtils.decrypt(encodedPassword);
        return encryption.equals(rawPassword.toString());
    }
}
