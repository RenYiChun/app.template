package com.lrenyi.template.core.coder.coder;

import java.nio.charset.StandardCharsets;
import java.security.spec.KeySpec;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import com.lrenyi.template.core.coder.TemplateEncryptService;
import com.lrenyi.template.core.util.Digests;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.text.StringEscapeUtils;
import org.kohsuke.MetaInfServices;
import org.springframework.util.StringUtils;

@Slf4j
@MetaInfServices
public class DefaultTemplateDataCoder implements TemplateEncryptService {
    
    /** 新格式前缀：PBKDF2-HMAC-SHA256 */
    private static final String V2_PREFIX = "v2:";
    private static final int PBKDF2_ITERATIONS = 100_000;
    private static final int SALT_LENGTH = 16;
    private static final int KEY_LENGTH = 256;

    @Override
    public String type() {
        return TemplateEncryptService.DEFAULT_ENCODER_KEY;
    }

    @Override
    public String decode(String encodedPassword) {
        return encodedPassword;
    }

    @Override
    public String encode(CharSequence rawPassword) {
        String plain = StringEscapeUtils.unescapeHtml4(rawPassword.toString());
        byte[] salt = Digests.generateSalt(SALT_LENGTH);
        byte[] hash = pbkdf2(plain.toCharArray(), salt, PBKDF2_ITERATIONS);
        String s = new String(Hex.encodeHex(hash));
        return V2_PREFIX + PBKDF2_ITERATIONS + ":" + new String(Hex.encodeHex(salt)) + ":" + s;
    }
    
    @Override
    public boolean matches(CharSequence rawPassword, String encodedPassword) {
        if (!StringUtils.hasLength(rawPassword)) {
            return false;
        }
        String plain = StringEscapeUtils.unescapeHtml4(rawPassword.toString());
        byte[] plainBytes = plain.getBytes(StandardCharsets.UTF_8);
        try {
            if (encodedPassword.startsWith(V2_PREFIX)) {
                return matchesV2(plainBytes, encodedPassword);
            }
            return matchesLegacySha1(plainBytes, encodedPassword);
        } catch (Exception e) {
            log.debug("Password format validation failed", e);
            return false;
        }
    }
    
    private boolean matchesV2(byte[] plainBytes, String encoded) throws DecoderException {
        String[] parts = encoded.substring(V2_PREFIX.length()).split(":", 3);
        if (parts.length != 3) {
            return false;
        }
        int iterations = Integer.parseInt(parts[0]);
        byte[] salt = Hex.decodeHex(parts[1].toCharArray());
        byte[] expectedHash = Hex.decodeHex(parts[2].toCharArray());
        char[] plainChars = new String(plainBytes, StandardCharsets.UTF_8).toCharArray();
        byte[] actualHash = pbkdf2(plainChars, salt, iterations);
        return java.util.Arrays.equals(expectedHash, actualHash);
    }
    
    private boolean matchesLegacySha1(byte[] plainBytes, String encoded) throws DecoderException {
        if (encoded.length() != 56) {
            return false;
        }
        byte[] salt = Hex.decodeHex(encoded.substring(0, 16).toCharArray());
        byte[] hashPassword = Digests.sha1(plainBytes, salt, 1024);
        String espwd = new String(Hex.encodeHex(salt)) + new String(Hex.encodeHex(hashPassword));
        return encoded.equals(espwd);
    }
    
    private static byte[] pbkdf2(char[] password, byte[] salt, int iterations) {
        try {
            KeySpec spec = new PBEKeySpec(password, salt, iterations, KEY_LENGTH);
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            return factory.generateSecret(spec).getEncoded();
        } catch (Exception e) {
            throw new IllegalStateException("PBKDF2 failed", e);
        }
    }
}
