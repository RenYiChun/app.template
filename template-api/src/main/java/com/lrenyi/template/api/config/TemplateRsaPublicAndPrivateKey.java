package com.lrenyi.template.api.config;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import com.lrenyi.template.core.util.RsaUtils;

public class TemplateRsaPublicAndPrivateKey extends RsaPublicAndPrivateKey {
    
    @Override
    public RSAPublicKey templateRSAPublicKey() {
        try {
            return RsaUtils.loadPublicKeyFromFile("rsa_public.pem");
        } catch (IOException | NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new IllegalStateException("Failed to load RSA public key", e);
        }
    }
    
    @Override
    public RSAPrivateKey templateRSAPrivateKey() {
        try {
            return RsaUtils.loadPrivateKeyFromFile("rsa_private.pem");
        } catch (IOException | NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new IllegalStateException("Failed to load RSA private key", e);
        }
    }
}
