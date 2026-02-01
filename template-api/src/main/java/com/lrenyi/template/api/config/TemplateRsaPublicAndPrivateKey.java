package com.lrenyi.template.api.config;

import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import com.lrenyi.template.core.util.RsaUtils;

public class TemplateRsaPublicAndPrivateKey extends RsaPublicAndPrivateKey {
    
    @Override
    public RSAPublicKey templateRSAPublicKey() {
        try {
            return RsaUtils.loadPublicKeyFromFile("rsa_public.pem");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    
    @Override
    public RSAPrivateKey templateRSAPrivateKey() {
        try {
            return RsaUtils.loadPrivateKeyFromFile("rsa_private.pem");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
