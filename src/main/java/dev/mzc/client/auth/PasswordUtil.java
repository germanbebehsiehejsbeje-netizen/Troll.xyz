package dev.mzc.client.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public class PasswordUtil {
    private static final String PEPPER = "MZC_AUTH_PEPPER_2026";

    public static String hashPassword(String username, String password) {
        if (username == null || password == null) {
            return null;
        }
        String u = username.trim();
        String p = password;
        if (u.isEmpty() || p.isEmpty()) {
            return null;
        }
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            String raw = "MZC_AUTH|" + u + "|" + p + "|" + PEPPER;
            byte[] digest = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(digest);
        } catch (Exception e) {
            return null;
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}

