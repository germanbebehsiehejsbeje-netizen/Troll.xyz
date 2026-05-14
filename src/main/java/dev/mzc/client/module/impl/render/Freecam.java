package dev.mzc.client.module.impl.render;

import com.mojang.authlib.GameProfile;
import dev.mzc.client.events.EventType;
import dev.mzc.client.events.client.TickEvent;
import dev.mzc.client.events.input.MoveInputEvent;
import dev.mzc.client.events.packet.PacketEvent;
import dev.mzc.client.module.Category;
import dev.mzc.client.module.Module;
import dev.mzc.client.values.impl.BoolValue;
import dev.mzc.client.values.impl.NumberValue;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.client.option.Perspective;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import java.util.UUID;

public class Freecam extends Module {
    private final NumberValue<Double> speed = new NumberValue<>("Speed", 1.0, 0.1, 5.0, 0.1);
    private final NumberValue<Double> verticalSpeed = new NumberValue<>("VerticalSpeed", 1.0, 0.1, 5.0, 0.1);
    private final BoolValue allowInteract = new BoolValue("AllowInteract", true);
    
    private Vec3d pos = Vec3d.ZERO;
    private Vec3d prevPos = Vec3d.ZERO;
    private Vec3d velocity = Vec3d.ZERO;
    
    private float yaw;
    private float pitch;
    private float lastYaw;
    private float lastPitch;
    
    private Perspective perspective;
    
    private boolean forward;
    private boolean backward;
    private boolean right;
    private boolean left;
    private boolean up;
    private boolean down;
    private boolean sprint;

    private OtherClientPlayerEntity fakePlayer;

    public Freecam() {
        super("Freecam", Category.Render);
        this.setType(ModuleType.All);
    }

    @Override
    protected void onEnable() {
        if (nullCheck()) {
            toggle();
            return;
        }

        this.yaw = mc.player.getYaw();
        this.pitch = mc.player.getPitch();
        this.perspective = mc.options.getPerspective();
        this.pos = new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ());
        this.prevPos = this.pos;

        if (this.perspective == Perspective.THIRD_PERSON_FRONT) {
            this.yaw += 180.0f;
            this.pitch *= -1.0f;
        }
        this.lastYaw = this.yaw;
        this.lastPitch = this.pitch;

        mc.options.setPerspective(Perspective.FIRST_PERSON);
        
        forward = false;
        backward = false;
        right = false;
        left = false;
        up = false;
        down = false;
        sprint = false;
        velocity = Vec3d.ZERO;

        spawnFakePlayer();
    }

    @Override
    protected void onDisable() {
        if (nullCheck()) return;

        if (this.perspective != null) {
            mc.options.setPerspective(this.perspective);
        }

        removeFakePlayer();
    }

    private void spawnFakePlayer() {
        fakePlayer = new OtherClientPlayerEntity(mc.world, new GameProfile(UUID.randomUUID(), mc.player.getName().getString()));
        fakePlayer.copyPositionAndRotation(mc.player);
        fakePlayer.bodyYaw = mc.player.bodyYaw;
        fakePlayer.headYaw = mc.player.headYaw;
        fakePlayer.getInventory().clone(mc.player.getInventory());
        fakePlayer.getAttributes().setFrom(mc.player.getAttributes());
        fakePlayer.setPose(mc.player.getPose());
        
        mc.world.addEntity(fakePlayer);
    }

    private void removeFakePlayer() {
        if (fakePlayer != null) {
            fakePlayer.discard();
            fakePlayer = null;
        }
    }

    @EventHandler
    private void onMoveInput(MoveInputEvent event) {
        forward = event.getForward() > 0;
        backward = event.getForward() < 0;
        left = event.getStrafe() > 0;
        right = event.getStrafe() < 0;
        up = event.isJump();
        down = event.isSneak();
        sprint = event.isSprint();

        event.setForward(0);
        event.setStrafe(0);
        event.setJump(false);
        event.setSneak(false);
        event.setSprint(false);
    }

    @EventHandler
    public void onTick(TickEvent.Pre event) {
        if (nullCheck()) return;

        if (mc.options.getPerspective() != Perspective.FIRST_PERSON) {
            mc.options.setPerspective(Perspective.FIRST_PERSON);
        }

        double yawRad = Math.toRadians(this.yaw);
        Vec3d forwardVec = new Vec3d(-Math.sin(yawRad), 0.0, Math.cos(yawRad));
        double rightYawRad = Math.toRadians(this.yaw + 90.0f);
        Vec3d rightVec = new Vec3d(-Math.sin(rightYawRad), 0.0, Math.cos(rightYawRad));

        double velX = 0.0;
        double velY = 0.0;
        double velZ = 0.0;

        double s = 0.5;
        if (sprint) {
            s = 1.0;
        }

        boolean a = false;
        if (this.forward) {
            velX += forwardVec.x * s * speed.get();
            velZ += forwardVec.z * s * speed.get();
            a = true;
        }
        if (this.backward) {
            velX -= forwardVec.x * s * speed.get();
            velZ -= forwardVec.z * s * speed.get();
            a = true;
        }

        boolean b = false;
        if (this.right) {
            velX += rightVec.x * s * speed.get();
            velZ += rightVec.z * s * speed.get();
            b = true;
        }
        if (this.left) {
            velX -= rightVec.x * s * speed.get();
            velZ -= rightVec.z * s * speed.get();
            b = true;
        }

        if (a && b) {
            double diagonal = 1.0 / Math.sqrt(2.0);
            velX *= diagonal;
            velZ *= diagonal;
        }

        if (this.up) {
            velY += s * verticalSpeed.get();
        }
        if (this.down) {
            velY -= s * verticalSpeed.get();
        }

        velocity = new Vec3d(
            velocity.x * 0.8875 + velX * 0.1125,
            velocity.y * 0.6 + velY * 0.4,
            velocity.z * 0.8875 + velZ * 0.1125
        );

        if (velocity.lengthSquared() < 0.0001) {
            velocity = Vec3d.ZERO;
        }

        this.prevPos = this.pos;
        this.pos = this.pos.add(velocity);
    }

    public void changeLookDirection(double deltaX, double deltaY) {
        this.lastYaw = this.yaw;
        this.lastPitch = this.pitch;
        this.yaw += (float) (deltaX * 0.15);
        this.pitch += (float) (deltaY * 0.15);
        this.pitch = MathHelper.clamp(this.pitch, -90.0f, 90.0f);
    }

    public double getX(float tickDelta) {
        return MathHelper.lerp(tickDelta, this.prevPos.x, this.pos.x);
    }

    public double getY(float tickDelta) {
        return MathHelper.lerp(tickDelta, this.prevPos.y, this.pos.y);
    }

    public double getZ(float tickDelta) {
        return MathHelper.lerp(tickDelta, this.prevPos.z, this.pos.z);
    }

    public double getYaw(float tickDelta) {
        return MathHelper.lerpAngleDegrees(tickDelta, this.lastYaw, this.yaw);
    }

    public double getPitch(float tickDelta) {
        return MathHelper.lerp(tickDelta, this.lastPitch, this.pitch);
    }

    public boolean allowInteract() {
        return allowInteract.get();
    }
}
