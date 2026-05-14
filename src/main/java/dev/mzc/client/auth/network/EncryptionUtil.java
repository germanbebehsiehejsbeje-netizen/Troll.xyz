package dev.mzc.client.auth.network;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.spec.SecretKeySpec;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;

public class EncryptionUtil {
    private static final String SECRET = "MZC_CLIENT_SECURE_KEY_2026_VERSION_FINAL_DO_NOT_LEAK";
    private static SecretKeySpec keySpec;

    static {
        try {
            byte[] key = SECRET.getBytes(StandardCharsets.UTF_8);
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            key = sha.digest(key);
            key = Arrays.copyOf(key, 16); // Use only first 128 bit
            keySpec = new SecretKeySpec(key, "AES");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static OutputStream encrypt(OutputStream out) {
        try {
            Cipher cipher = Cipher.getInstance("AES/CTR/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, new javax.crypto.spec.IvParameterSpec(keySpec.getEncoded()));
            return new CipherOutputStream(out, cipher);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static InputStream decrypt(InputStream in) {
        try {
            Cipher cipher = Cipher.getInstance("AES/CTR/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, keySpec, new javax.crypto.spec.IvParameterSpec(keySpec.getEncoded()));
            return new CipherInputStream(in, cipher);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
