package dev.mzc.client.auth;

public enum UserRole {
    USER("普通用户"),
    VIP("VIP"),
    SUPER_VIP("超级VIP"),
    DEV("开发者");

    private final String displayName;

    UserRole(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean isAtLeast(UserRole role) {
        return this.ordinal() >= role.ordinal();
    }
}
