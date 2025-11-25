package pt.isec.common.dto.auth;

import java.io.Serializable;

public record UpdateStudentDTO(
        Integer userId,
        Integer studentNumber,
        String name,
        String email,
        String oldPassword,
        String newPassword
) implements Serializable {}
