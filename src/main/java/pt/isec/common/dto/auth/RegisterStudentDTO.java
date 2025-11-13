package pt.isec.common.dto.auth;

import java.io.Serializable;

public record RegisterStudentDTO(
        String name,
        String email,
        String password,
        Integer studentNumber
) implements Serializable {}
