package dev.mzc.client.utils.math;

public enum AngleConnection {
    INSTANCE;

    private float yaw, pitch;

    public void rotateTo(Angle angle, int step, AngleConfig config, TaskPriority priority, Object parent) {
        // Устанавливаем углы для отправки в RotationUpdateEvent
        this.yaw = angle.getYaw();
        this.pitch = angle.getPitch();
    }

    public void clear() {
        // Сброс состояния
    }
}