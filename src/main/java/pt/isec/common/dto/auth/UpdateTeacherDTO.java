package pt.isec.common.dto.auth;

import java.io.Serializable;

public record UpdateTeacherDTO(
        Integer userId,
        Integer teacherId,
        String name,
        String email,
        String oldPassword,
        String newPassword
) implements Serializable {}
