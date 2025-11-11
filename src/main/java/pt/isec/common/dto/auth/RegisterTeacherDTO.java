package pt.isec.common.dto.auth;

import java.io.Serializable;

public record RegisterTeacherDTO(
        String name,
        String email,
        String password,
        String teacherRegisterCode
) implements Serializable {}
