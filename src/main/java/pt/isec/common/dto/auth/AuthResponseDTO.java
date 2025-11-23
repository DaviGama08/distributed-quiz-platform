package pt.isec.common.dto.auth;

import java.io.Serializable;

public record AuthResponseDTO(
        String sessionId,
        String userId,
        String userType,
        String name,
        String email
) implements Serializable {}
