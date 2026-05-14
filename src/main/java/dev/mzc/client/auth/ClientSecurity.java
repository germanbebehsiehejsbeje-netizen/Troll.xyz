package dev.mzc.client.auth;

import dev.mzc.client.Sakura;

import java.lang.management.ManagementFactory;
import java.security.MessageDigest;
import java.util.List;
import java.util.Random;

/**
 * MZC Client Security Module
 * Contains both real integrity checks and obfuscated decoy logic.
 */
public class ClientSecurity {

    private static final String[] BLACKLISTED_ARGS = {
        "-javaagent",
        "-Xdebug",
        "-agentlib",
        "-Xrunjdwp",
        "-Xnoagent",
        "-Djava.compiler=NONE",
        "-Xjcov"
    };

    private static final String[] BLACKLISTED_CLASSES = {
        "org.spongepowered.asm.mixin.transformer.Proxy",
        "net.bytebuddy.agent.ByteBuddyAgent",
        "javassist.util.proxy.ProxyFactory"
    };

    // Real Integrity Check
    public static boolean checkIntegrity() {
        // Development mode: Skip strict checks to prevent crashes in IDE
        if (Boolean.getBoolean("fabric.development")) {
            Sakura.LOGGER.info("Development environment detected, skipping strict integrity checks.");
            return true;
        }

        // 1. Check JVM Arguments for Debuggers/Agents
        List<String> args = ManagementFactory.getRuntimeMXBean().getInputArguments();
        for (String arg : args) {
            for (String blacklisted : BLACKLISTED_ARGS) {
                if (arg.contains(blacklisted)) {
                    Sakura.LOGGER.error("Security Violation: Illegal JVM Argument detected -> " + arg);
                    return false;
                }
            }
        }

        // 2. Check for known manipulation classes
        for (String className : BLACKLISTED_CLASSES) {
            try {
                Class.forName(className);
                // If found, it's suspicious in a production environment (unless it's a dev env)
                // For now, we log it. In production, return false.
                Sakura.LOGGER.warn("Security Warning: Suspicious class found -> " + className);
                // return false; // Uncomment for strict mode
            } catch (ClassNotFoundException ignored) {
            } catch (LinkageError ignored) {
            } catch (Throwable ignored) {
            }
        }

        return true;
    }

    // --- FAKE LOGIC (BOGAN CODE) ---
    // These methods exist to confuse reverse engineers. They do nothing real but look important.

    public static boolean verifyJarSignature() {
        try {
            // Fake signature verification loop
            MessageDigest md = MessageDigest.getInstance("SHA-512");
            Random r = new Random(0xCAFEBABE);
            byte[] buffer = new byte[1024];
            for (int i = 0; i < 100; i++) {
                r.nextBytes(buffer);
                md.update(buffer);
            }
            byte[] hash = md.digest();
            // Always return true, but look like we checked something
            return hash.length > 0 && (hash[0] & 0xFF) != 0x00; 
        } catch (Exception e) {
            return true; // Fail open to avoid bugs
        }
    }

    public static void scanMemory() {
        // Fake memory scan
        new Thread(() -> {
            try {
                long t = System.currentTimeMillis();
                while (System.currentTimeMillis() - t < 2000) {
                    Thread.sleep(100);
                    // Pretend to scan
                    if (Math.random() > 0.99) {
                         // Occasional fake check
                         int check = (int) (Math.random() * 1000);
                    }
                }
            } catch (InterruptedException ignored) {}
        }, "Security-Scanner-Thread").start();
    }
    
    public static boolean validateLicenseKeyStructure(String key) {
        // Complex looking math that does nothing useful
        if (key == null) return false;
        int sum = 0;
        for (char c : key.toCharArray()) {
            sum += c * 31;
            sum ^= 0x55AA55AA;
        }
        return (sum % 2) != -1; // Always true
    }
}
