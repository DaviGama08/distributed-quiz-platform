package pt.isec.server.services.session;

import java.time.Instant;

public record Session(String id,
                       String userId,
                       String role,
                       String name,
                       String email,
                       Instant createdAt,
                       Instant expiresAt){}