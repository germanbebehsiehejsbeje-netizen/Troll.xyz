package dev.mzc.client.auth.network;

import dev.mzc.client.auth.HWIDUtil;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;

public class AuthClient {
    
    private static final byte[] HOST_BYTES = {49, 49, 50, 46, 52, 52, 46, 50, 50, 50, 46, 52, 57};
    private static final int PORT_VAL = 34566;
    
    private static String getHost() {
        return new String(HOST_BYTES, StandardCharsets.UTF_8);
    }
    
    public Result authenticate(String cardKey) {
        if (cardKey == null || cardKey.trim().isEmpty()) {
            return Result.fail("卡密不能为空");
        }
        
        String hwid = HWIDUtil.getHWID();
        if (!HWIDUtil.validateHWIDFormat(hwid)) {
            return Result.fail("HWID无效");
        }
        
        try (Socket socket = new Socket(getHost(), PORT_VAL)) {
            socket.setSoTimeout(10000);
            
            // Apply Encryption Wrapper
            InputStream rawIn = socket.getInputStream();
            OutputStream rawOut = socket.getOutputStream();
            
            DataInputStream in = new DataInputStream(EncryptionUtil.decrypt(rawIn));
            DataOutputStream out = new DataOutputStream(EncryptionUtil.encrypt(rawOut));
            
            String challenge = generateChallenge();
            
            out.writeUTF("AUTH");
            out.writeUTF(cardKey.trim());
            out.writeUTF(hwid);
            out.writeUTF(challenge);
            out.flush();
            
            String response = in.readUTF();
            
            if ("SUCCESS".equals(response)) {
                String serverChallenge = in.readUTF();
                if (verifyChallenge(challenge, serverChallenge)) {
                    return Result.ok("验证成功");
                } else {
                    return Result.fail("安全验证失败");
                }
            }
            
            String message = in.readUTF();
            return Result.fail(message);
            
        } catch (Exception e) {
            return Result.connectionError("连接失败(请寻找DEV开启验证服务器)");
        }
    }
    
    public boolean validateHWID(String hwid) {
        if (hwid == null || !HWIDUtil.validateHWIDFormat(hwid)) {
            return false;
        }
        
        try (Socket socket = new Socket(getHost(), PORT_VAL)) {
            socket.setSoTimeout(5000);
            
            // Apply Encryption Wrapper
            InputStream rawIn = socket.getInputStream();
            OutputStream rawOut = socket.getOutputStream();
            
            DataInputStream in = new DataInputStream(EncryptionUtil.decrypt(rawIn));
            DataOutputStream out = new DataOutputStream(EncryptionUtil.encrypt(rawOut));
            
            out.writeUTF("VALIDATE_HWID");
            out.writeUTF(hwid);
            out.flush();
            
            String response = in.readUTF();
            return "SUCCESS".equals(response);
            
        } catch (Exception e) {
            return false;
        }
    }
    
    public boolean validateSession(String cardKey, String hwid) {
        if (cardKey == null || hwid == null || !HWIDUtil.validateHWIDFormat(hwid)) {
            return false;
        }
        
        try (Socket socket = new Socket(getHost(), PORT_VAL)) {
            socket.setSoTimeout(5000);
            
            // Apply Encryption Wrapper
            InputStream rawIn = socket.getInputStream();
            OutputStream rawOut = socket.getOutputStream();
            
            DataInputStream in = new DataInputStream(EncryptionUtil.decrypt(rawIn));
            DataOutputStream out = new DataOutputStream(EncryptionUtil.encrypt(rawOut));
            
            out.writeUTF("SESSION_CHECK");
            out.writeUTF(cardKey);
            out.writeUTF(hwid);
            out.flush();
            
            String response = in.readUTF();
            return "VALID".equals(response);
            
        } catch (Exception e) {
            return false;
        }
    }

    public Result registerAccount(String cardKey, String username, String password) {
        if (cardKey == null || cardKey.trim().isEmpty()) {
            return Result.fail("卡密不能为空");
        }
        if (username == null || username.trim().isEmpty()) {
            return Result.fail("用户名不能为空");
        }
        if (password == null || password.trim().isEmpty()) {
            return Result.fail("密码不能为空");
        }

        String hwid = HWIDUtil.getHWID();
        if (!HWIDUtil.validateHWIDFormat(hwid)) {
            return Result.fail("HWID无效");
        }

        try (Socket socket = new Socket(getHost(), PORT_VAL)) {
            socket.setSoTimeout(10000);
            
            // Apply Encryption Wrapper
            InputStream rawIn = socket.getInputStream();
            OutputStream rawOut = socket.getOutputStream();
            
            DataInputStream in = new DataInputStream(EncryptionUtil.decrypt(rawIn));
            DataOutputStream out = new DataOutputStream(EncryptionUtil.encrypt(rawOut));

            out.writeUTF("REGISTER_ACCOUNT");
            out.writeUTF(cardKey.trim());
            out.writeUTF(username.trim());
            out.writeUTF(password.trim());
            out.writeUTF(hwid);
            out.flush();

            String response = in.readUTF();
            String message = in.readUTF();
            if ("SUCCESS".equals(response)) {
                String role = in.readUTF();
                return Result.ok(message, role);
            }
            return Result.fail(message);
        } catch (Exception e) {
            return Result.connectionError("连接失败: " + e.getMessage());
        }
    }

    public Result loginAccount(String username, String password) {
        if (username == null || username.trim().isEmpty()) {
            return Result.fail("用户名不能为空");
        }
        if (password == null || password.trim().isEmpty()) {
            return Result.fail("密码不能为空");
        }

        String hwid = HWIDUtil.getHWID();
        if (!HWIDUtil.validateHWIDFormat(hwid)) {
            return Result.fail("HWID无效");
        }

        try (Socket socket = new Socket(getHost(), PORT_VAL)) {
            socket.setSoTimeout(10000);
            
            // Apply Encryption Wrapper
            InputStream rawIn = socket.getInputStream();
            OutputStream rawOut = socket.getOutputStream();
            
            DataInputStream in = new DataInputStream(EncryptionUtil.decrypt(rawIn));
            DataOutputStream out = new DataOutputStream(EncryptionUtil.encrypt(rawOut));

            out.writeUTF("LOGIN_ACCOUNT");
            out.writeUTF(username.trim());
            out.writeUTF(password.trim());
            out.writeUTF(hwid);
            out.flush();

            String response = in.readUTF();
            String message = in.readUTF();
            if ("SUCCESS".equals(response)) {
                String role = in.readUTF();
                return Result.ok(message, role);
            }
            return Result.fail(message);
        } catch (Exception e) {
            return Result.connectionError("连接失败: " + e.getMessage());
        }
    }
    
    private static String generateChallenge() {
        try {
            SecureRandom random = new SecureRandom();
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 16; i++) {
                sb.append(Integer.toHexString(random.nextInt(16)));
            }
            return sb.toString();
        } catch (Exception e) {
            return "challenge";
        }
    }
    
    private static boolean verifyChallenge(String original, String response) {
        if (original == null || response == null) {
            return false;
        }
        try {
            String expected = hashChallenge(original);
            return expected.equals(response);
        } catch (Exception e) {
            return false;
        }
    }
    
    private static String hashChallenge(String challenge) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            String salted = "ZP_CHALLENGE_" + challenge + "_SALT_2026";
            byte[] digest = md.digest(salted.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString().substring(0, 16);
        } catch (Exception e) {
            return "";
        }
    }

    public static class Result {
        public final boolean success;
        public final String message;
        public final boolean isConnectionError;
        public final dev.mzc.client.auth.UserRole role;

        public Result(boolean success, String message, boolean isConnectionError) {
            this(success, message, isConnectionError, null);
        }
        
        public Result(boolean success, String message, boolean isConnectionError, dev.mzc.client.auth.UserRole role) {
            this.success = success;
            this.message = message;
            this.isConnectionError = isConnectionError;
            this.role = role;
        }

        public static Result ok(String message) {
            return new Result(true, message == null ? "OK" : message, false);
        }
        
        public static Result ok(String message, String roleName) {
            dev.mzc.client.auth.UserRole r = dev.mzc.client.auth.UserRole.USER;
            try {
                if (roleName != null) {
                    r = dev.mzc.client.auth.UserRole.valueOf(roleName);
                }
            } catch (Exception ignored) {}
            return new Result(true, message == null ? "OK" : message, false, r);
        }

        public static Result fail(String message) {
            return new Result(false, message == null ? "FAILED" : message, false);
        }

        public static Result connectionError(String message) {
            return new Result(false, message == null ? "Connection Failed" : message, true);
        }
    }
}
