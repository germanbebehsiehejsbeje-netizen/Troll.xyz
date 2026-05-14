package dev.mzc.client.auth;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

public class SecureStorage {
    
    private static final String AUTH_FILE = ".zp_cache";
    private static final byte[] MAGIC_BYTES = {0x5A, 0x50, 0x41, 0x55, 0x54, 0x48, 0x32, 0x30};
    private static final int VERSION = 2;
    
    private static final byte[] SALT_PART_1 = {0x5A, 0x50, 0x5F, 0x53, 0x45, 0x43, 0x55, 0x52, 0x45};
    private static final byte[] SALT_PART_2 = {0x5F, 0x4B, 0x45, 0x59, 0x5F, 0x32, 0x30, 0x32, 0x36};
    private static final byte[] IV_PART = {0x41, 0x55, 0x54, 0x48, 0x5F, 0x49, 0x56, 0x5F, 0x53, 0x41, 0x4C, 0x54, 0x5F, 0x58, 0x59, 0x5A};
    
    private static byte[] getEncryptionKey() {
        try {
            String hwid = HWIDUtil.getHWID();
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            
            byte[] combined = new byte[SALT_PART_1.length + hwid.getBytes(StandardCharsets.UTF_8).length + SALT_PART_2.length];
            System.arraycopy(SALT_PART_1, 0, combined, 0, SALT_PART_1.length);
            System.arraycopy(hwid.getBytes(StandardCharsets.UTF_8), 0, combined, SALT_PART_1.length, hwid.getBytes(StandardCharsets.UTF_8).length);
            System.arraycopy(SALT_PART_2, 0, combined, SALT_PART_1.length + hwid.getBytes(StandardCharsets.UTF_8).length, SALT_PART_2.length);
            
            byte[] digest = md.digest(combined);
            byte[] key = new byte[32];
            System.arraycopy(digest, 0, key, 0, 32);
            return key;
        } catch (Exception e) {
            return new byte[32];
        }
    }
    
    private static IvParameterSpec getIV() {
        try {
            String hwid = HWIDUtil.getHWID();
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] combined = new byte[IV_PART.length + hwid.getBytes(StandardCharsets.UTF_8).length];
            System.arraycopy(IV_PART, 0, combined, 0, IV_PART.length);
            System.arraycopy(hwid.getBytes(StandardCharsets.UTF_8), 0, combined, IV_PART.length, hwid.getBytes(StandardCharsets.UTF_8).length);
            return new IvParameterSpec(md.digest(combined));
        } catch (Exception e) {
            return new IvParameterSpec(new byte[16]);
        }
    }
    
    public static void saveAuthData(String cardKey, String hwid, long timestamp) {
        try {
            SecureRandom random = new SecureRandom();
            byte[] nonce = new byte[16];
            random.nextBytes(nonce);
            
            String data = cardKey + "|" + hwid + "|" + timestamp + "|" + generateHMAC(cardKey, hwid, timestamp, nonce);
            
            byte[] encrypted = encrypt(data, nonce);
            if (encrypted == null) return;
            
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos);
            
            dos.write(MAGIC_BYTES);
            dos.writeInt(VERSION);
            dos.write(nonce);
            dos.writeInt(encrypted.length);
            dos.write(encrypted);
            dos.flush();
            
            File file = new File(AUTH_FILE);
            file.createNewFile();
            file.setReadable(true, true);
            file.setWritable(true, true);
            
            try (FileOutputStream fos = new FileOutputStream(file)) {
                fos.write(baos.toByteArray());
            }
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    public static AuthData loadAuthData() {
        try {
            File file = new File(AUTH_FILE);
            if (!file.exists()) return null;
            
            byte[] fileContent;
            try (FileInputStream fis = new FileInputStream(file)) {
                fileContent = fis.readAllBytes();
            }
            
            if (fileContent.length < 8 + 4 + 16 + 4) return null;
            
            ByteArrayInputStream bais = new ByteArrayInputStream(fileContent);
            DataInputStream dis = new DataInputStream(bais);
            
            byte[] magic = new byte[8];
            dis.readFully(magic);
            
            for (int i = 0; i < MAGIC_BYTES.length; i++) {
                if (magic[i] != MAGIC_BYTES[i]) return null;
            }
            
            int version = dis.readInt();
            if (version != VERSION) return null;
            
            byte[] nonce = new byte[16];
            dis.readFully(nonce);
            
            int encryptedLen = dis.readInt();
            if (encryptedLen <= 0 || encryptedLen > 1024) return null;
            
            byte[] encrypted = new byte[encryptedLen];
            dis.readFully(encrypted);
            
            String decrypted = decrypt(encrypted, nonce);
            if (decrypted == null) return null;
            
            String[] parts = decrypted.split("\\|");
            if (parts.length != 4) return null;
            
            String cardKey = parts[0];
            String hwid = parts[1];
            long timestamp = Long.parseLong(parts[2]);
            String hmac = parts[3];
            
            if (!verifyHMAC(cardKey, hwid, timestamp, nonce, hmac)) return null;
            
            return new AuthData(cardKey, hwid, timestamp);
            
        } catch (Exception e) {
            return null;
        }
    }
    
    public static boolean deleteAuthData() {
        try {
            File file = new File(AUTH_FILE);
            if (file.exists()) {
                try (RandomAccessFile raf = new RandomAccessFile(file, "rw")) {
                    byte[] random = new byte[(int) raf.length()];
                    new SecureRandom().nextBytes(random);
                    raf.write(random);
                }
                return file.delete();
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    
    private static byte[] encrypt(String data, byte[] nonce) {
        try {
            byte[] key = getEncryptionKey();
            SecretKeySpec secretKey = new SecretKeySpec(key, "AES");
            IvParameterSpec iv = getIV();
            
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, iv);
            
            byte[] dataBytes = data.getBytes(StandardCharsets.UTF_8);
            byte[] combined = new byte[nonce.length + dataBytes.length];
            System.arraycopy(nonce, 0, combined, 0, nonce.length);
            System.arraycopy(dataBytes, 0, combined, nonce.length, dataBytes.length);
            
            return cipher.doFinal(combined);
        } catch (Exception e) {
            return null;
        }
    }
    
    private static String decrypt(byte[] encrypted, byte[] expectedNonce) {
        try {
            byte[] key = getEncryptionKey();
            SecretKeySpec secretKey = new SecretKeySpec(key, "AES");
            IvParameterSpec iv = getIV();
            
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, secretKey, iv);
            
            byte[] decrypted = cipher.doFinal(encrypted);
            
            if (decrypted.length < 16) return null;
            
            byte[] nonce = new byte[16];
            System.arraycopy(decrypted, 0, nonce, 0, 16);
            
            for (int i = 0; i < 16; i++) {
                if (nonce[i] != expectedNonce[i]) return null;
            }
            
            return new String(decrypted, 16, decrypted.length - 16, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }
    
    private static String generateHMAC(String cardKey, String hwid, long timestamp, byte[] nonce) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            
            byte[] cardKeyBytes = cardKey.getBytes(StandardCharsets.UTF_8);
            byte[] hwidBytes = hwid.getBytes(StandardCharsets.UTF_8);
            byte[] tsBytes = longToBytes(timestamp);
            
            byte[] combined = new byte[cardKeyBytes.length + hwidBytes.length + 8 + nonce.length + SALT_PART_1.length + SALT_PART_2.length];
            int offset = 0;
            
            System.arraycopy(SALT_PART_1, 0, combined, offset, SALT_PART_1.length);
            offset += SALT_PART_1.length;
            System.arraycopy(cardKeyBytes, 0, combined, offset, cardKeyBytes.length);
            offset += cardKeyBytes.length;
            System.arraycopy(hwidBytes, 0, combined, offset, hwidBytes.length);
            offset += hwidBytes.length;
            System.arraycopy(tsBytes, 0, combined, offset, 8);
            offset += 8;
            System.arraycopy(nonce, 0, combined, offset, nonce.length);
            offset += nonce.length;
            System.arraycopy(SALT_PART_2, 0, combined, offset, SALT_PART_2.length);
            
            byte[] digest = md.digest(combined);
            return bytesToHex(digest).substring(0, 32);
        } catch (Exception e) {
            return "INVALID";
        }
    }
    
    private static boolean verifyHMAC(String cardKey, String hwid, long timestamp, byte[] nonce, String hmac) {
        String expected = generateHMAC(cardKey, hwid, timestamp, nonce);
        return expected.equals(hmac);
    }
    
    private static byte[] longToBytes(long l) {
        byte[] result = new byte[8];
        for (int i = 7; i >= 0; i--) {
            result[i] = (byte)(l & 0xFF);
            l >>= 8;
        }
        return result;
    }
    
    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
    
    public static class AuthData {
        public final String cardKey;
        public final String hwid;
        public final long timestamp;
        
        public AuthData(String cardKey, String hwid, long timestamp) {
            this.cardKey = cardKey;
            this.hwid = hwid;
            this.timestamp = timestamp;
        }
    }
}
