package pt.isec.server.services.auth;

import pt.isec.common.dto.auth.*;

import java.sql.SQLException;

public interface IAuthService {
    LoginResponseDTO registerTeacher(RegisterTeacherDTO registerTeacherDTO) throws SQLException;
    LoginResponseDTO registerStudent(RegisterStudentDTO registerStudentDTO) throws SQLException;
    LoginResponseDTO login(LoginRequestDTO loginRequestDTO);
    void changePassword(ChangePasswordDTO changePasswordDTO);
}
