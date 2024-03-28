package com.lrenyi.template.core.util;

import com.alibaba.fastjson2.JSON;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import javax.crypto.Cipher;
import lombok.Getter;
import org.apache.commons.codec.binary.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RsaUtils {
    private static final Logger logger = LoggerFactory.getLogger(RsaUtils.class);
    @Getter
    private static PrivateKey privateKey;
    @Getter
    private static PublicKey publicKey;
    
    static {
        try {
            publicKey = loadPublicKeyFromFile("rsa_public.pem");
            privateKey = loadPrivateKeyFromFile("rsa_private.pem");
        } catch (Throwable cause) {
            logger.error("加载系统所需要的RSA公钥，私钥过程中出现异常", cause);
            System.exit(1);
        }
    }
    
    public static String encryption(String data) {
        if (publicKey == null) {
            return data;
        }
        try {
            Cipher cipher = Cipher.getInstance("RSA");
            cipher.init(Cipher.ENCRYPT_MODE, publicKey);
            byte[] decryptedBytes = cipher.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.encodeBase64String(decryptedBytes);
        } catch (Throwable cause) {
            logger.error("", cause);
        }
        return data;
    }
    
    public static String decrypt(String data) {
        if (privateKey == null) {
            return data;
        }
        byte[] decoded = Base64.decodeBase64(data);
        try {
            Cipher cipher = Cipher.getInstance("RSA");
            cipher.init(Cipher.DECRYPT_MODE, privateKey);
            byte[] decryptedBytes = cipher.doFinal(decoded);
            return new String(decryptedBytes, StandardCharsets.UTF_8);
        } catch (Throwable cause) {
            logger.error("", cause);
        }
        return data;
    }
    
    public static String publicKeyString() {
        String publicKeyFileName = "rsa_public.pem";
        if (FileUtil.isResourceFileNotExists(publicKeyFileName)) {
            publicKeyFileName = "default_rsa_public.pem";
        }
        try (InputStream inputStream = RsaUtils.class.getClassLoader().getResourceAsStream(publicKeyFileName)) {
            if (inputStream == null) {
                throw new NullPointerException("loader file：" + publicKeyFileName + " faild");
            }
            byte[] keyBytes = inputStream.readAllBytes();
            return new String(keyBytes);
        } catch (Throwable cause) {
            logger.error("", cause);
            return null;
        }
    }
    
    public static Map<String, String> createKeys(int keySize) {
        //为RSA算法创建一个KeyPairGenerator对象
        KeyPairGenerator kpg;
        try {
            kpg = KeyPairGenerator.getInstance("RSA");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalArgumentException("No such algorithm-->[" + "RSA" + "]");
        }
        //初始化KeyPairGenerator对象,密钥长度
        kpg.initialize(keySize);
        //生成密匙对
        KeyPair keyPair = kpg.generateKeyPair();
        //得到公钥
        Key publicKey = keyPair.getPublic();
        String publicKeyStr = Base64.encodeBase64String(publicKey.getEncoded());
        //得到私钥
        Key privateKey = keyPair.getPrivate();
        String privateKeyStr = Base64.encodeBase64String(privateKey.getEncoded());
        Map<String, String> keyPairMap = new HashMap<>();
        keyPairMap.put("publicKey", publicKeyStr);
        keyPairMap.put("privateKey", privateKeyStr);
        return keyPairMap;
    }
    
    public static void convertPublicKeyToPem(RSAPublicKey publicKey, String pemFilename) {
        byte[] encodedPublicKey = publicKey.getEncoded();
        String publicKeyPem = "-----BEGIN PUBLIC KEY-----\n";
        publicKeyPem += Base64.encodeBase64String(encodedPublicKey).replaceAll("(.{64})", "$1\n");
        publicKeyPem += "\n-----END PUBLIC KEY-----";
        try (FileWriter writer = new FileWriter(pemFilename)) {
            writer.write(publicKeyPem);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    
    public static void convertPrivateKeyToPemFile(RSAPrivateKey privateKey, String pemFilename) {
        byte[] encodedPrivateKey = privateKey.getEncoded();
        String privateKeyPem = "-----BEGIN PRIVATE KEY-----\n";
        privateKeyPem += Base64.encodeBase64String(encodedPrivateKey).replaceAll("(.{64})", "$1\n");
        privateKeyPem += "\n-----END PRIVATE KEY-----";
        
        try (FileWriter writer = new FileWriter(pemFilename)) {
            writer.write(privateKeyPem);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    
    public static RSAPublicKey loadPublicKeyFromFile(String publicKeyFileName) throws Exception {
        if (FileUtil.isResourceFileNotExists(publicKeyFileName)) {
            publicKeyFileName = "default_rsa_public.pem";
        }
        try (InputStream inputStream = RsaUtils.class.getClassLoader().getResourceAsStream(publicKeyFileName)) {
            if (inputStream == null) {
                throw new NullPointerException("loader file：" + publicKeyFileName + " faild");
            }
            byte[] keyBytes = inputStream.readAllBytes();
            String publicKeyContent = new String(keyBytes);
            publicKeyContent = publicKeyContent.replaceAll("\\n", "")
                                               .replace("-----BEGIN PUBLIC KEY-----", "")
                                               .replace("-----END PUBLIC KEY-----", "");
            return makeRSAPublicKeyFromString(publicKeyContent);
        }
    }
    
    public static RSAPublicKey makeRSAPublicKeyFromString(String context) throws NoSuchAlgorithmException,
            InvalidKeySpecException {
        KeyFactory kf = KeyFactory.getInstance("RSA");
        X509EncodedKeySpec keySpecX509 = new X509EncodedKeySpec(Base64.decodeBase64(context));
        PublicKey publicKey = kf.generatePublic(keySpecX509);
        return (RSAPublicKey) publicKey;
    }
    
    public static RSAPrivateKey loadPrivateKeyFromFile(String privateKeyFileName) throws Exception {
        if (FileUtil.isResourceFileNotExists(privateKeyFileName)) {
            privateKeyFileName = "default_rsa_private.pem";
        }
        try (InputStream inputStream = RsaUtils.class.getClassLoader().getResourceAsStream(privateKeyFileName)) {
            if (inputStream == null) {
                throw new NullPointerException("loader file：" + privateKeyFileName + " faild");
            }
            byte[] keyBytes = inputStream.readAllBytes();
            String privateKeyContent = new String(keyBytes);
            privateKeyContent = privateKeyContent.replaceAll("\\n", "")
                                                 .replace("-----BEGIN PRIVATE KEY-----", "")
                                                 .replace("-----END PRIVATE KEY-----", "");
            return makeRSAPrivateKeyFromString(privateKeyContent);
        }
    }
    
    public static RSAPrivateKey makeRSAPrivateKeyFromString(String context) throws NoSuchAlgorithmException,
            InvalidKeySpecException {
        KeyFactory kf = KeyFactory.getInstance("RSA");
        PKCS8EncodedKeySpec keySpecPkcs8 = new PKCS8EncodedKeySpec(Base64.decodeBase64(context));
        PrivateKey privateKey = kf.generatePrivate(keySpecPkcs8);
        return (RSAPrivateKey) privateKey;
    }
    
    public static void makeJwtString() {
        String kid = UUID.randomUUID().toString();
        RSAKey rsaKey = new RSAKey.Builder((RSAPublicKey) publicKey).privateKey(privateKey).keyID(kid).build();
        Map<String, Object> jsonObject = new JWKSet(rsaKey).toJSONObject(true);
        try (FileWriter writer = new FileWriter("default_rsa_public.jwt")) {
            writer.write(JSON.toJSONString(jsonObject));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    
    public static String getJwtKid() {
        String jwtFileName = "rsa_public.jwt";
        if (FileUtil.isResourceFileNotExists(jwtFileName)) {
            jwtFileName = "default_rsa_public.jwt";
        }
        try (InputStream inputStream = RsaUtils.class.getClassLoader().getResourceAsStream(jwtFileName)) {
            if (inputStream == null) {
                throw new NullPointerException("loader file：" + jwtFileName + " faild");
            }
            byte[] keyBytes = inputStream.readAllBytes();
            String value = new String(keyBytes);
            return String.valueOf(JSON.parseObject(value).get("kid"));
        } catch (Throwable cause) {
            logger.error("", cause);
            return null;
        }
    }
}
