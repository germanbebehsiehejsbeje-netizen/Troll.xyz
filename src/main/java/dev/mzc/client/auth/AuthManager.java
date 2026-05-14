package dev.mzc.client.auth;

import dev.mzc.client.Sakura;
import dev.mzc.client.auth.network.AuthClient;

public class AuthManager {
    
    private static boolean authenticated = false;
    private static String currentCardKey = null;
    private static String currentUsername = null;
    private static String currentPasswordHash = null;
    private static UserRole currentUserRole = UserRole.USER;
    private static long lastValidationTime = 0;
    private static final long VALIDATION_INTERVAL = 300000;
    
    public static boolean checkPassed = false;
    public static boolean authScreenForced = false;
    
    private static final AuthClient authClient = new AuthClient();

    public static UserRole getRole() {
        return currentUserRole;
    }

    public static void initialize() {
        Sakura.LOGGER.info("=== MZC-Client 验证系统初始化 ===");
        
        // Anti-Tamper / Security Check
        if (!ClientSecurity.checkIntegrity()) {
            Sakura.LOGGER.error("致命错误: 系统环境异常，拒绝运行");
            // In production, we might throw an exception or crash
            // throw new RuntimeException("Security Violation");
        }
        
        // Bogan Logic (Fake Security)
        ClientSecurity.verifyJarSignature();
        ClientSecurity.scanMemory();
        
        Sakura.LOGGER.info("检查本地账号缓存...");
        
        if (validateStoredAuth()) {
            authenticated = true;
            checkPassed = true;
            authScreenForced = false;
            Sakura.LOGGER.info("✓ 验证完成 - 本地缓存有效");
        } else {
            Sakura.LOGGER.info("✗ 需要验证 - 缓存无效或已过期");
            checkPassed = false;
            authScreenForced = false;
            AccountStorage.delete();
        }
        
        Sakura.LOGGER.info("验证状态：checkPassed={}", checkPassed);
    }

    private static boolean validateStoredAuth() {
        try {
            AccountStorage.Credentials creds = AccountStorage.load();
            if (creds == null) {
                Sakura.LOGGER.info("无有效账号缓存");
                return false;
            }
            
            if (creds.username == null || creds.username.isBlank() || creds.password == null || creds.password.isBlank()) {
                return false;
            }

            Sakura.LOGGER.info("验证服务器端账号状态...");
            AuthClient.Result r = authClient.loginAccount(creds.username, creds.password);
            if (!r.success) {
                Sakura.LOGGER.info("服务器端验证失败: {}", r.message);
                return false;
            }
            
            currentUsername = creds.username;
            currentPasswordHash = creds.password;
            
            // Try parse role from response if possible, but loginAccount returns Result
            // We need to update AuthClient to return role in Result
            if (r.role != null) {
                currentUserRole = r.role;
            } else {
                currentUserRole = UserRole.USER;
            }

            lastValidationTime = System.currentTimeMillis();
            
            Sakura.LOGGER.info("服务器端验证通过，权限: " + currentUserRole.getDisplayName());
            return true;
            
        } catch (Exception e) {
            Sakura.LOGGER.error("验证缓存异常：", e);
            return false;
        }
    }

    public static AuthClient.Result performRegister(String cardKey, String username, String password) {
        if (cardKey == null || cardKey.trim().isEmpty()) {
            return AuthClient.Result.fail("卡密不能为空");
        }
        if (username == null || username.trim().isEmpty()) {
            return AuthClient.Result.fail("用户名不能为空");
        }
        if (password == null || password.isEmpty()) {
            return AuthClient.Result.fail("密码不能为空");
        }

        // Send plaintext password to server so admin can see it
        AuthClient.Result result = authClient.registerAccount(cardKey, username, password);
        
        if (result.success) {
            currentUsername = username;
            currentPasswordHash = password; // Store plaintext
            currentUserRole = result.role != null ? result.role : UserRole.USER;
            authenticated = true;
            checkPassed = true;
            authScreenForced = true;
            lastValidationTime = System.currentTimeMillis();
            Sakura.LOGGER.info("注册成功，已自动登录，权限: " + currentUserRole.getDisplayName());
            
            // Save plaintext credentials (encrypted in file)
            AccountStorage.save(username, password, System.currentTimeMillis());
        }
        
        return result;
    }

    public static AuthClient.Result performLogin(String username, String password, boolean rememberPassword) {
        if (username == null || username.trim().isEmpty()) {
            return AuthClient.Result.fail("用户名不能为空");
        }
        if (password == null || password.isEmpty()) {
            return AuthClient.Result.fail("密码不能为空");
        }

        // Send plaintext password
        AuthClient.Result result = authClient.loginAccount(username, password);
        
        if (result.success) {
            currentUsername = username;
            currentPasswordHash = password; // Store plaintext
            currentUserRole = result.role != null ? result.role : UserRole.USER;
            authenticated = true;
            checkPassed = true;
            authScreenForced = true;
            lastValidationTime = System.currentTimeMillis();
            Sakura.LOGGER.info("登录成功，权限: " + currentUserRole.getDisplayName());
            
            if (rememberPassword) {
                AccountStorage.save(username, password, System.currentTimeMillis());
            } else {
                AccountStorage.delete();
            }
        }
        
        return result;
    }

    public static boolean periodicValidation() {
        if (!authenticated || currentUsername == null || currentPasswordHash == null) {
            return false;
        }
        
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastValidationTime < VALIDATION_INTERVAL) {
            return true;
        }
        
        AuthClient.Result r = authClient.loginAccount(currentUsername, currentPasswordHash);
        if (r.success) {
            lastValidationTime = currentTime;
            return true;
        }

        Sakura.LOGGER.warn("会话验证失败，清除认证");
        clearAuthentication();
        return false;
    }

    public static void saveAuthentication() {
        if (currentUsername != null) {
            authenticated = true;
            checkPassed = true;
            authScreenForced = true;
        }
    }

    public static boolean isAuthenticated() {
        return authenticated;
    }

    public static void setAuthenticated(boolean auth) {
        authenticated = auth;
    }

    public static void clearAuthentication() {
        authenticated = false;
        checkPassed = false;
        authScreenForced = false;
        currentCardKey = null;
        currentUsername = null;
        currentPasswordHash = null;
        AccountStorage.delete();
    }
    
    public static String getCurrentCardKey() {
        return currentCardKey;
    }

    public static String getCurrentUsername() {
        return currentUsername;
    }

    public static AuthClient.Result performAuthentication(String cardKey) {
        if (cardKey == null || cardKey.trim().isEmpty()) {
            return AuthClient.Result.fail("卡密不能为空");
        }
        AuthClient.Result result = authClient.authenticate(cardKey);
        if (result.success) {
             currentCardKey = cardKey;
             authenticated = true;
             checkPassed = true;
             authScreenForced = true;
             lastValidationTime = System.currentTimeMillis();
             Sakura.LOGGER.info("卡密验证成功");
        }
        return result;
    }
}
