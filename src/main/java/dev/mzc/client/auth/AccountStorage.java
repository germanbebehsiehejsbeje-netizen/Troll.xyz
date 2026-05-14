package dev.mzc.client.auth;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

public class AccountStorage {
    private static final String FILE_NAME = ".mzc_account_cache";
    private static final byte[] MAGIC_BYTES = {0x4D, 0x5A, 0x43, 0x41, 0x43, 0x43, 0x32, 0x30};
    private static final int VERSION = 1;

    public static void save(String username, String password, long timestamp) {
        if (username == null || password == null) return;
        try {
            SecureRandom random = new SecureRandom();
            byte[] nonce = new byte[16];
            random.nextBytes(nonce);

            String data = username + "|" + password + "|" + timestamp + "|" + generateHmac(username, password, timestamp, nonce);
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

            try (FileOutputStream fos = new FileOutputStream(new File(FILE_NAME))) {
                fos.write(baos.toByteArray());
            }
        } catch (Exception ignored) {
        }
    }

    public static Credentials load() {
        File file = new File(FILE_NAME);
        if (!file.exists()) return null;
        try {
            byte[] fileContent;
            try (FileInputStream fis = new FileInputStream(file)) {
                fileContent = fis.readAllBytes();
            }
            if (fileContent.length < 8 + 4 + 16 + 4) return null;

            DataInputStream dis = new DataInputStream(new ByteArrayInputStream(fileContent));
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
            if (encryptedLen <= 0 || encryptedLen > 4096) return null;

            byte[] encrypted = new byte[encryptedLen];
            dis.readFully(encrypted);

            String decrypted = decrypt(encrypted, nonce);
            if (decrypted == null) return null;

            String[] parts = decrypted.split("\\|");
            if (parts.length != 4) return null;

            String username = parts[0];
            String password = parts[1];
            long ts = Long.parseLong(parts[2]);
            String hmac = parts[3];

            if (!verifyHmac(username, password, ts, nonce, hmac)) return null;
            return new Credentials(username, password, ts);
        } catch (Exception ignored) {
            return null;
        }
    }

    public static boolean delete() {
        try {
            File file = new File(FILE_NAME);
            if (!file.exists()) return true;
            try (RandomAccessFile raf = new RandomAccessFile(file, "rw")) {
                byte[] random = new byte[(int) raf.length()];
                new SecureRandom().nextBytes(random);
                raf.write(random);
            }
            return file.delete();
        } catch (Exception ignored) {
            return false;
        }
    }

    private static byte[] encrypt(String data, byte[] nonce) {
        try {
            SecretKeySpec secretKey = new SecretKeySpec(getKey(), "AES");
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, getIv());

            byte[] dataBytes = data.getBytes(StandardCharsets.UTF_8);
            byte[] combined = new byte[nonce.length + dataBytes.length];
            System.arraycopy(nonce, 0, combined, 0, nonce.length);
            System.arraycopy(dataBytes, 0, combined, nonce.length, dataBytes.length);
            return cipher.doFinal(combined);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String decrypt(byte[] encrypted, byte[] expectedNonce) {
        try {
            SecretKeySpec secretKey = new SecretKeySpec(getKey(), "AES");
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, secretKey, getIv());

            byte[] decrypted = cipher.doFinal(encrypted);
            if (decrypted.length < 16) return null;

            for (int i = 0; i < 16; i++) {
                if (decrypted[i] != expectedNonce[i]) return null;
            }

            return new String(decrypted, 16, decrypted.length - 16, StandardCharsets.UTF_8);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static byte[] getKey() {
        try {
            String hwid = HWIDUtil.getHWID();
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(("MZC_ACCOUNT_KEY|" + hwid + "|2026").getBytes(StandardCharsets.UTF_8));
            byte[] key = new byte[32];
            System.arraycopy(digest, 0, key, 0, 32);
            return key;
        } catch (Exception ignored) {
            return new byte[32];
        }
    }

    private static IvParameterSpec getIv() {
        try {
            String hwid = HWIDUtil.getHWID();
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(("MZC_ACCOUNT_IV|" + hwid).getBytes(StandardCharsets.UTF_8));
            return new IvParameterSpec(digest);
        } catch (Exception ignored) {
            return new IvParameterSpec(new byte[16]);
        }
    }

    private static String generateHmac(String username, String password, long timestamp, byte[] nonce) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            String raw = "MZC_ACC_HMAC|" + username + "|" + Base64.getEncoder().encodeToString(password.getBytes(StandardCharsets.UTF_8)) + "|" + timestamp + "|" + Base64.getEncoder().encodeToString(nonce);
            byte[] digest = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(digest).substring(0, 32);
        } catch (Exception ignored) {
            return "INVALID";
        }
    }

    private static boolean verifyHmac(String username, String password, long timestamp, byte[] nonce, String hmac) {
        return generateHmac(username, password, timestamp, nonce).equals(hmac);
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    public static class Credentials {
        public final String username;
        public final String password;
        public final long timestamp;

        public Credentials(String username, String password, long timestamp) {
            this.username = username;
            this.password = password;
            this.timestamp = timestamp;
        }
    }
}

