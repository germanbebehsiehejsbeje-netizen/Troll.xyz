package dev.mzc.client.auth;

import java.net.NetworkInterface;
import java.security.MessageDigest;
import java.util.*;

public class HWIDUtil {
    
    private static final String SALT = "ZP_2026_SECURE_HWID_SALT_X9K2M";
    
    public static String getHWID() {
        try {
            StringBuilder raw = new StringBuilder();
            
            Enumeration<NetworkInterface> networks = NetworkInterface.getNetworkInterfaces();
            List<String> macs = new ArrayList<>();
            
            for (NetworkInterface network : Collections.list(networks)) {
                if (!network.isLoopback() && network.isUp()) {
                    byte[] mac = network.getHardwareAddress();
                    if (mac != null && mac.length == 6) {
                        StringBuilder macStr = new StringBuilder();
                        for (byte b : mac) {
                            macStr.append(String.format("%02X", b));
                        }
                        macs.add(macStr.toString());
                    }
                }
            }
            
            Collections.sort(macs);
            for (String mac : macs) {
                raw.append(mac);
            }
            
            String userName = System.getProperty("user.name", "unknown");
            String osName = System.getProperty("os.name", "unknown");
            String osArch = System.getProperty("os.arch", "unknown");
            String userHome = System.getProperty("user.home", "unknown");
            
            raw.append(userName).append(osName).append(osArch).append(userHome);
            raw.append(SALT);
            
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(raw.toString().getBytes("UTF-8"));
            
            return bytesToHex(digest).substring(0, 32);
            
        } catch (Exception e) {
            return generateFallbackHWID();
        }
    }
    
    private static String generateFallbackHWID() {
        try {
            StringBuilder raw = new StringBuilder();
            raw.append(System.getProperty("user.name", "x"));
            raw.append(System.getProperty("os.name", "x"));
            raw.append(System.getProperty("os.arch", "x"));
            raw.append(System.getProperty("java.home", "x"));
            raw.append(SALT);
            
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(raw.toString().getBytes("UTF-8"));
            return bytesToHex(digest).substring(0, 32);
        } catch (Exception e) {
            return "FALLBACK_" + System.currentTimeMillis();
        }
    }
    
    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
    
    public static boolean validateHWIDFormat(String hwid) {
        if (hwid == null || hwid.length() != 32) {
            return false;
        }
        for (char c : hwid.toCharArray()) {
            if (!Character.isLetterOrDigit(c)) {
                return false;
            }
        }
        return true;
    }
}
