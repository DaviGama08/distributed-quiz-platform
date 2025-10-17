package pt.isec.common.dto.auth;

public record RegisterStudentDTO(String name, String email, String password,
                                Integer studentNumber) {}
