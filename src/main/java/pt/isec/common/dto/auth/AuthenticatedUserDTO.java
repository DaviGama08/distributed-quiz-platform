package pt.isec.common.dto.auth;

import java.io.Serializable;

public record AuthenticatedUserDTO(
        String id,
        String name,
        String email,
        String userType
) implements Serializable {}
