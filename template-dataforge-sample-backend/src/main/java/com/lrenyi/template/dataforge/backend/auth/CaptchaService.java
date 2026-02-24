package com.lrenyi.template.dataforge.backend.auth;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

/**
 * 验证码生成与校验服务，内存存储，TTL 5 分钟。
 */
@Service
public class CaptchaService {

    private static final int WIDTH = 120;
    private static final int HEIGHT = 40;
    private static final int CODE_LEN = 4;
    private static final long TTL_MS = 5 * 60 * 1000;
    private static final String CHARS = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";

    private final Map<String, CaptchaEntry> store = new ConcurrentHashMap<>();

    public CaptchaResult generate() {
        String key = UUID.randomUUID().toString();
        String code = randomCode();
        store.put(key, new CaptchaEntry(code, System.currentTimeMillis()));
        evictExpired();
        String imageBase64 = "data:image/png;base64," + Base64.getEncoder().encodeToString(drawImage(code));
        return new CaptchaResult(key, imageBase64);
    }

    public boolean verify(String key, String code) {
        if (key == null || code == null) {
            return false;
        }
        CaptchaEntry entry = store.remove(key);
        return entry != null && entry.code.equalsIgnoreCase(code.trim())
                && (System.currentTimeMillis() - entry.createTime) < TTL_MS;
    }

    private String randomCode() {
        SecureRandom r = new SecureRandom();
        StringBuilder sb = new StringBuilder(CODE_LEN);
        for (int i = 0; i < CODE_LEN; i++) {
            sb.append(CHARS.charAt(r.nextInt(CHARS.length())));
        }
        return sb.toString();
    }

    private byte[] drawImage(String code) {
        BufferedImage img = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, WIDTH, HEIGHT);
        g.setColor(Color.LIGHT_GRAY);
        SecureRandom r = new SecureRandom();
        for (int i = 0; i < 6; i++) {
            g.drawLine(r.nextInt(WIDTH), r.nextInt(HEIGHT), r.nextInt(WIDTH), r.nextInt(HEIGHT));
        }
        g.setFont(new Font("Arial", Font.BOLD, 24));
        int x = 12;
        for (char c : code.toCharArray()) {
            g.setColor(new Color(r.nextInt(100) + 50, r.nextInt(100) + 50, r.nextInt(100) + 50));
            g.drawString(String.valueOf(c), x, 28);
            x += 26;
        }
        g.dispose();
        try {
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            javax.imageio.ImageIO.write(img, "png", baos);
            return baos.toByteArray();
        } catch (java.io.IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void evictExpired() {
        long now = System.currentTimeMillis();
        store.entrySet().removeIf(e -> (now - e.getValue().createTime) > TTL_MS);
    }

    public record CaptchaResult(String key, String imageBase64) {
    }

    private static class CaptchaEntry {
        final String code;
        final long createTime;

        CaptchaEntry(String code, long createTime) {
            this.code = code;
            this.createTime = createTime;
        }
    }
}
