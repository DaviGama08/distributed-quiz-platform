package pt.isec.server.services.auth;

import pt.isec.common.dto.auth.*;

import java.sql.SQLException;

public interface IAuthService {
    LoginResponseDTO registerTeacher(RegisterTeacherDTO registerTeacherDTO) throws Exception;
    LoginResponseDTO registerStudent(RegisterStudentDTO registerStudentDTO) throws Exception;
    LoginResponseDTO login(LoginRequestDTO dto) throws Exception;
    void changePassword(ChangePasswordDTO changePasswordDTO);
}
