package com.lrenyi.template.core.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
import javax.crypto.Cipher;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Base64;

@Slf4j
public class RsaUtils {
    private static final Object loadLock = new Object();
    /** RSA 使用 OAEP padding，避免 PKCS#1 v1.5 的 Bleichenbacher 攻击 */
    private static final String RSA_OAEP = "RSA/ECB/OAEPWithSHA-256AndMGF1Padding";
    /** 旧格式兼容（PKCS#1 v1.5），仅用于解密已有数据 */
    private static final String RSA_LEGACY = "RSA";
    private static final String OAEP_PREFIX = "OAEP:";
    private static volatile PrivateKey privateKey;
    private static volatile PublicKey publicKey;
    private static volatile boolean keysLoaded;
    
    public static PrivateKey getPrivateKey() {
        ensureKeysLoaded();
        return privateKey;
    }

    private static void ensureKeysLoaded() {
        if (keysLoaded) {
            return;
        }
        synchronized (loadLock) {
            if (keysLoaded) {
                return;
            }
            try {
                publicKey = loadPublicKeyFromFile("rsa_public.pem");
                privateKey = loadPrivateKeyFromFile("rsa_private.pem");
                keysLoaded = true;
            } catch (Throwable cause) {
                log.error("加载系统所需要的RSA公钥，私钥过程中出现异常", cause);
                throw new IllegalStateException("Failed to load RSA keys", cause);
            }
        }
    }

    public static RSAPublicKey loadPublicKeyFromFile(String publicKeyFileName) throws Exception {
        if (FileUtil.isResourceFileNotExists(publicKeyFileName)) {
            publicKeyFileName = "default_rsa_public.pem";
        }
        try (InputStream inputStream = RsaUtils.class.getClassLoader().getResourceAsStream(publicKeyFileName)) {
            if (inputStream == null) {
                throw new NullPointerException("loader file：" + publicKeyFileName + " failed");
            }
            byte[] keyBytes = inputStream.readAllBytes();
            String publicKeyContent = new String(keyBytes, StandardCharsets.UTF_8);
            publicKeyContent = publicKeyContent.replaceAll("\\n", "")
                                               .replace("-----BEGIN PUBLIC KEY-----", "")
                                               .replace("-----END PUBLIC KEY-----", "");
            return makeRSAPublicKeyFromString(publicKeyContent);
        }
    }
    
    public static RSAPrivateKey loadPrivateKeyFromFile(String privateKeyFileName) throws Exception {
        if (FileUtil.isResourceFileNotExists(privateKeyFileName)) {
            privateKeyFileName = "default_rsa_private.pem";
        }
        try (InputStream inputStream = RsaUtils.class.getClassLoader().getResourceAsStream(privateKeyFileName)) {
            if (inputStream == null) {
                throw new NullPointerException("loader file：" + privateKeyFileName + " failed");
            }
            byte[] keyBytes = inputStream.readAllBytes();
            String privateKeyContent = new String(keyBytes, StandardCharsets.UTF_8);
            privateKeyContent = privateKeyContent.replaceAll("\\n", "")
                                                 .replace("-----BEGIN PRIVATE KEY-----", "")
                                                 .replace("-----END PRIVATE KEY-----", "");
            return makeRSAPrivateKeyFromString(privateKeyContent);
        }
    }
    
    public static RSAPublicKey makeRSAPublicKeyFromString(String context) throws NoSuchAlgorithmException,
            InvalidKeySpecException {
        KeyFactory kf = KeyFactory.getInstance("RSA");
        X509EncodedKeySpec keySpecX509 = new X509EncodedKeySpec(Base64.decodeBase64(context));
        PublicKey publicKey = kf.generatePublic(keySpecX509);
        return (RSAPublicKey) publicKey;
    }
    
    public static RSAPrivateKey makeRSAPrivateKeyFromString(String context) throws NoSuchAlgorithmException,
            InvalidKeySpecException {
        KeyFactory kf = KeyFactory.getInstance("RSA");
        PKCS8EncodedKeySpec keySpecPkcs8 = new PKCS8EncodedKeySpec(Base64.decodeBase64(context));
        PrivateKey privateKey = kf.generatePrivate(keySpecPkcs8);
        return (RSAPrivateKey) privateKey;
    }
    
    public static PublicKey getPublicKey() {
        ensureKeysLoaded();
        return publicKey;
    }
    
    public static String encryption(String data) {
        ensureKeysLoaded();
        if (publicKey == null) {
            return data;
        }
        try {
            Cipher cipher = Cipher.getInstance(RSA_OAEP);
            cipher.init(Cipher.ENCRYPT_MODE, publicKey);
            byte[] encrypted = cipher.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return OAEP_PREFIX + Base64.encodeBase64String(encrypted);
        } catch (Throwable cause) {
            log.error("RSA encryption failed", cause);
            throw new IllegalStateException("RSA encryption failed", cause);
        }
    }
    
    public static String decrypt(String data) {
        ensureKeysLoaded();
        if (privateKey == null) {
            return data;
        }
        boolean useOaep = data.startsWith(OAEP_PREFIX);
        String base64 = useOaep ? data.substring(OAEP_PREFIX.length()) : data;
        byte[] decoded = Base64.decodeBase64(base64);
        String transformation = useOaep ? RSA_OAEP : RSA_LEGACY;
        try {
            Cipher cipher = Cipher.getInstance(transformation);
            cipher.init(Cipher.DECRYPT_MODE, privateKey);
            byte[] decrypted = cipher.doFinal(decoded);
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Throwable cause) {
            log.error("RSA decryption failed", cause);
            throw new IllegalStateException("RSA decryption failed", cause);
        }
    }
    
    public static String publicKeyString() {
        String publicKeyFileName = "rsa_public.pem";
        if (FileUtil.isResourceFileNotExists(publicKeyFileName)) {
            publicKeyFileName = "default_rsa_public.pem";
        }
        try (InputStream inputStream = RsaUtils.class.getClassLoader().getResourceAsStream(publicKeyFileName)) {
            if (inputStream == null) {
                throw new NullPointerException("loader file：" + publicKeyFileName + " failed");
            }
            byte[] keyBytes = inputStream.readAllBytes();
            return new String(keyBytes, StandardCharsets.UTF_8);
        } catch (Throwable cause) {
            log.error("Failed to load public key from file: {}", publicKeyFileName, cause);
            throw new IllegalStateException("Failed to load public key from file: " + publicKeyFileName, cause);
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
        Path path = validateAndResolvePath(pemFilename);
        byte[] encodedPublicKey = publicKey.getEncoded();
        String publicKeyPem = "-----BEGIN PUBLIC KEY-----\n";
        publicKeyPem += Base64.encodeBase64String(encodedPublicKey).replaceAll("(.{64})", "$1\n");
        publicKeyPem += "\n-----END PUBLIC KEY-----";
        try (var writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            writer.write(publicKeyPem);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    
    public static void convertPrivateKeyToPemFile(RSAPrivateKey privateKey, String pemFilename) {
        Path path = validateAndResolvePath(pemFilename);
        byte[] encodedPrivateKey = privateKey.getEncoded();
        String privateKeyPem = "-----BEGIN PRIVATE KEY-----\n";
        privateKeyPem += Base64.encodeBase64String(encodedPrivateKey).replaceAll("(.{64})", "$1\n");
        privateKeyPem += "\n-----END PRIVATE KEY-----";
        
        try (var writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            writer.write(privateKeyPem);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    
    /**
     * 校验路径避免路径遍历攻击，仅允许当前工作目录下的相对路径。
     */
    private static Path validateAndResolvePath(String filename) {
        if (filename == null || filename.isEmpty()) {
            throw new IllegalArgumentException("filename must not be empty");
        }
        if (filename.contains("..") || filename.startsWith("/")) {
            throw new IllegalArgumentException("path traversal or absolute path not allowed: " + filename);
        }
        Path path = Paths.get(filename).normalize();
        if (path.startsWith("..")) {
            throw new IllegalArgumentException("path traversal not allowed: " + filename);
        }
        return path;
    }
    
}
