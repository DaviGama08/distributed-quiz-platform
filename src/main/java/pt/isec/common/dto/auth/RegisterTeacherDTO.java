package pt.isec.common.dto.auth;

public record RegisterTeacherDTO(String name, String email, String password,
                                 String teacherRegisterCode) {}
