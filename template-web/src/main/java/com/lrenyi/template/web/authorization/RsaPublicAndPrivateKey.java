package com.lrenyi.template.web.authorization;

import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public abstract class RsaPublicAndPrivateKey {
    
    private String kid;
    
    public abstract RSAPublicKey templateRSAPublicKey();
    
    public abstract RSAPrivateKey templateRSAPrivateKey();
}
