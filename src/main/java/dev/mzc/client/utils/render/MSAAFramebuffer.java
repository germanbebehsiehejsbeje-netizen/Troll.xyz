package dev.mzc.client.utils.render;

import net.minecraft.client.gl.Framebuffer;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 1.21.11 compatibility shim:
 * keeps the old call surface used by mixins/modules while routing to a safe fallback path.
 */
public final class MSAAFramebuffer {
    public static final int MIN_SAMPLES = 2;
    public static final int MAX_SAMPLES = 16;

    private static final Map<Integer, MSAAFramebuffer> INSTANCES = new ConcurrentHashMap<>();
    private static final AtomicInteger ACTIVE_USES = new AtomicInteger(0);

    private final int samples;

    private MSAAFramebuffer(int samples) {
        this.samples = Math.max(MIN_SAMPLES, Math.min(MAX_SAMPLES, samples));
    }

    public static boolean framebufferInUse() {
        return ACTIVE_USES.get() > 0;
    }

    public static MSAAFramebuffer getInstance(int samples) {
        return INSTANCES.computeIfAbsent(samples, MSAAFramebuffer::new);
    }

    public static void use(Runnable drawAction) {
        use(MAX_SAMPLES, null, drawAction);
    }

    public static void use(int samples, Framebuffer mainBuffer, Runnable drawAction) {
        // Preserve call contract. 1.21.11 removed several old FBO internals this class relied on.
        // Fallback: execute draw path directly, keeping behavior stable and compile-safe.
        ACTIVE_USES.incrementAndGet();
        try {
            drawAction.run();
        } finally {
            ACTIVE_USES.decrementAndGet();
        }
    }

    public int getSamples() {
        return samples;
    }
}
