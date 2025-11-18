package pt.isec.common.dto.auth;

import java.io.Serializable;

public record ChangePasswordDTO(
        Integer sessionId,
        String oldPassword,
        String newPassword,
        String userType
) implements Serializable {}
