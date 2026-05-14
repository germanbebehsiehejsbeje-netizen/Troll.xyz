package dev.mzc.client.shaders;

public class CoreShaders {
    public static final ShaderTicker shaderTicker = new ShaderTicker();

    public static class ShaderTicker {
        private final long startTime = System.currentTimeMillis();

        public float getPassedTime() {
            return (float) (System.currentTimeMillis() - startTime);
        }
    }
}
