package com.lrenyi.template.core.coder.coder;

import com.lrenyi.template.core.coder.TemplateDataCoder;
import com.lrenyi.template.core.util.Digests;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.text.StringEscapeUtils;
import org.kohsuke.MetaInfServices;
import org.springframework.util.StringUtils;

@MetaInfServices
public class DefaultTemplateDataCoder implements TemplateDataCoder {
    
    @Override
    public String type() {
        return TemplateDataCoder.DEFAULT_ENCODER_KEY;
    }
    
    @Override
    public String decode(String encodedPassword) {
        return encodedPassword;
    }
    
    @Override
    public String encode(CharSequence rawPassword) {
        String plain = StringEscapeUtils.unescapeHtml4(rawPassword.toString());
        byte[] salt = Digests.generateSalt(8);
        byte[] hashPassword = Digests.sha1(plain.getBytes(), salt, 1024);
        return new String(Hex.encodeHex(salt)) + new String(Hex.encodeHex(hashPassword));
    }
    
    @Override
    public boolean matches(CharSequence rawPassword, String encodedPassword) {
        if (!StringUtils.hasLength(rawPassword)) {
            return false;
        } else {
            String plain = StringEscapeUtils.unescapeHtml4(rawPassword.toString());
            
            try {
                byte[] salt = Hex.decodeHex(encodedPassword.substring(0, 16).toCharArray());
                byte[] hashPassword = Digests.sha1(plain.getBytes(), salt, 1024);
                String espwd =
                        new String(Hex.encodeHex(salt)) + new String(Hex.encodeHex(hashPassword));
                return encodedPassword.equals(espwd);
            } catch (Exception var6) {
                return false;
            }
        }
    }
}
