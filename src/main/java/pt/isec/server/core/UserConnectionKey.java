package pt.isec.server.core;

import java.util.Locale;

/** Identifies an active connection without colliding across role-specific ID spaces. */
public record UserConnectionKey(String role, long userId) {
    public UserConnectionKey {
        if (role == null || role.isBlank()) {
            throw new IllegalArgumentException("role is required");
        }
        role = role.trim().toUpperCase(Locale.ROOT);
        if (!"TEACHER".equals(role) && !"STUDENT".equals(role)) {
            throw new IllegalArgumentException("unsupported role");
        }
        if (userId <= 0) {
            throw new IllegalArgumentException("userId must be positive");
        }
    }
}
