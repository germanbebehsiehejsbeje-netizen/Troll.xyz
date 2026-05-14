package dev.mzc.satin.impl;

import dev.mzc.satin.api.uniform.*;
import net.minecraft.client.gl.GlUniform;
import net.minecraft.client.gl.ShaderProgram;
import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.lang.reflect.Method;
import java.util.Arrays;

public final class ManagedUniform extends ManagedUniformBase implements
        Uniform1i, Uniform2i, Uniform3i, Uniform4i,
        Uniform1f, Uniform2f, Uniform3f, Uniform4f,
        UniformMat4 {

    private static final GlUniform[] NO_TARGETS = new GlUniform[0];

    private final int count;

    private GlUniform[] targets = NO_TARGETS;
    private int i0, i1, i2, i3;
    private float f0, f1, f2, f3;
    private Matrix4f matrix4f = new Matrix4f();
    private float[] arrayValue = new float[0];
    private boolean firstUpload = true;
    private UploadKind uploadKind = UploadKind.NONE;

    private enum UploadKind {
        NONE,
        INT1, INT2, INT3, INT4,
        FLOAT1, FLOAT2, FLOAT3, FLOAT4,
        MATRIX4,
        FLOAT_ARRAY
    }

    public ManagedUniform(String name, int count) {
        super(name);
        this.count = count;
    }

    @Override
    public boolean findUniformTarget(ShaderProgram shader) {
        GlUniform uniform = shader.getUniform(this.name);
        if (uniform != null) {
            this.targets = new GlUniform[]{uniform};
            this.syncCurrentValues();
            return true;
        } else {
            this.targets = NO_TARGETS;
            return false;
        }
    }

    private void syncCurrentValues() {
        if (!this.firstUpload) {
            switch (this.uploadKind) {
                case INT1 -> uploadToTargets(i0);
                case INT2 -> uploadToTargets(i0, i1);
                case INT3 -> uploadToTargets(i0, i1, i2);
                case INT4 -> uploadToTargets(i0, i1, i2, i3);
                case FLOAT1 -> uploadToTargets(f0);
                case FLOAT2 -> uploadToTargets(f0, f1);
                case FLOAT3 -> uploadToTargets(f0, f1, f2);
                case FLOAT4 -> uploadToTargets(f0, f1, f2, f3);
                case MATRIX4 -> uploadToTargets(new Matrix4f(matrix4f));
                case FLOAT_ARRAY -> uploadToTargets(arrayValue.clone());
                case NONE -> {
                }
            }
        }
    }

    @Override
    public void set(int value) {
        GlUniform[] targets = this.targets;
        int nbTargets = targets.length;
        if (nbTargets > 0) {
            if (firstUpload || i0 != value) {
                uploadToTargets(value);
                i0 = value;
                uploadKind = UploadKind.INT1;
                firstUpload = false;
            }
        }
    }

    @Override
    public void set(int value0, int value1) {
        GlUniform[] targets = this.targets;
        int nbTargets = targets.length;
        if (nbTargets > 0) {
            if (firstUpload || i0 != value0 || i1 != value1) {
                uploadToTargets(value0, value1);
                i0 = value0;
                i1 = value1;
                uploadKind = UploadKind.INT2;
                firstUpload = false;
            }
        }
    }

    @Override
    public void set(int value0, int value1, int value2) {
        GlUniform[] targets = this.targets;
        int nbTargets = targets.length;
        if (nbTargets > 0) {
            if (firstUpload || i0 != value0 || i1 != value1 || i2 != value2) {
                uploadToTargets(value0, value1, value2);
                i0 = value0;
                i1 = value1;
                i2 = value2;
                uploadKind = UploadKind.INT3;
                firstUpload = false;
            }
        }
    }

    @Override
    public void set(int value0, int value1, int value2, int value3) {
        GlUniform[] targets = this.targets;
        int nbTargets = targets.length;
        if (nbTargets > 0) {
            if (firstUpload || i0 != value0 || i1 != value1 || i2 != value2 || i3 != value3) {
                uploadToTargets(value0, value1, value2, value3);
                i0 = value0;
                i1 = value1;
                i2 = value2;
                i3 = value3;
                uploadKind = UploadKind.INT4;
                firstUpload = false;
            }
        }
    }

    @Override
    public void set(float value) {
        GlUniform[] targets = this.targets;
        int nbTargets = targets.length;
        if (nbTargets > 0) {
            if (firstUpload || f0 != value) {
                uploadToTargets(value);
                f0 = value;
                uploadKind = UploadKind.FLOAT1;
                firstUpload = false;
            }
        }
    }

    @Override
    public void set(float value0, float value1) {
        GlUniform[] targets = this.targets;
        int nbTargets = targets.length;
        if (nbTargets > 0) {
            if (firstUpload || f0 != value0 || f1 != value1) {
                uploadToTargets(value0, value1);
                f0 = value0;
                f1 = value1;
                uploadKind = UploadKind.FLOAT2;
                firstUpload = false;
            }
        }
    }

    @Override
    public void set(Vector2f value) {
        set(value.x(), value.y());
    }

    @Override
    public void set(float value0, float value1, float value2) {
        GlUniform[] targets = this.targets;
        int nbTargets = targets.length;
        if (nbTargets > 0) {
            if (firstUpload || f0 != value0 || f1 != value1 || f2 != value2) {
                uploadToTargets(value0, value1, value2);
                f0 = value0;
                f1 = value1;
                f2 = value2;
                uploadKind = UploadKind.FLOAT3;
                firstUpload = false;
            }
        }
    }

    @Override
    public void set(Vector3f value) {
        set(value.x(), value.y(), value.z());
    }

    @Override
    public void set(float value0, float value1, float value2, float value3) {
        GlUniform[] targets = this.targets;
        int nbTargets = targets.length;
        if (nbTargets > 0) {
            if (firstUpload || f0 != value0 || f1 != value1 || f2 != value2 || f3 != value3) {
                uploadToTargets(value0, value1, value2, value3);
                f0 = value0;
                f1 = value1;
                f2 = value2;
                f3 = value3;
                uploadKind = UploadKind.FLOAT4;
                firstUpload = false;
            }
        }
    }

    @Override
    public void set(Vector4f value) {
        set(value.x(), value.y(), value.z(), value.w());
    }

    @Override
    public void set(Matrix4f value) {
        GlUniform[] targets = this.targets;
        int nbTargets = targets.length;
        if (nbTargets > 0) {
            uploadToTargets(value);
            matrix4f = new Matrix4f(value);
            uploadKind = UploadKind.MATRIX4;
            firstUpload = false;
        }
    }

    @Override
    public void setFromArray(float[] values) {
        if (this.count != values.length) {
            throw new IllegalArgumentException("Mismatched values size, expected " + count + " but got " + values.length);
        }

        GlUniform[] targets = this.targets;
        int nbTargets = targets.length;
        if (nbTargets > 0) {
            if (firstUpload || !Arrays.equals(arrayValue, values)) {
                uploadToTargets(values);
                arrayValue = values.clone();
                uploadKind = UploadKind.FLOAT_ARRAY;
                firstUpload = false;
            }
        }
    }

    private void uploadToTargets(Object... args) {
        for (GlUniform target : this.targets) {
            invokeCompatibleSetter(target, args);
        }
    }

    private static void invokeCompatibleSetter(GlUniform target, Object... args) {
        for (Method method : target.getClass().getMethods()) {
            if (method.getReturnType() != Void.TYPE) continue;
            Class<?>[] parameterTypes = method.getParameterTypes();
            if (parameterTypes.length != args.length) continue;
            if (!matches(parameterTypes, args)) continue;
            try {
                method.invoke(target, args);
                return;
            } catch (ReflectiveOperationException ignored) {
                // Keep searching for another compatible setter.
            }
        }
    }

    private static boolean matches(Class<?>[] parameterTypes, Object[] args) {
        for (int i = 0; i < parameterTypes.length; i++) {
            if (!isCompatible(parameterTypes[i], args[i])) return false;
        }
        return true;
    }

    private static boolean isCompatible(Class<?> parameterType, Object arg) {
        if (arg == null) return !parameterType.isPrimitive();
        if (parameterType.isInstance(arg)) return true;

        if (arg instanceof Integer) return parameterType == Integer.TYPE || parameterType == Integer.class;
        if (arg instanceof Float) return parameterType == Float.TYPE || parameterType == Float.class;
        if (arg instanceof float[]) return parameterType == float[].class;
        if (arg instanceof Matrix4f) return Matrix4f.class.isAssignableFrom(parameterType);

        return false;
    }
}
