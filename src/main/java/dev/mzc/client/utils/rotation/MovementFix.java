package dev.mzc.client.utils.rotation;

public enum MovementFix {
    OFF("Off"),
    NORMAL("Normal"),
    TRADITIONAL("Traditional"),
    BACKWARDS_SPRINT("Backwards Sprint"),
    GRIM("Grim"); // Added Grim mode for strict anti-cheat servers

    final String name;

    MovementFix(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return name;
    }
}