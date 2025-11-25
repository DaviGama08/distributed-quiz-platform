package pt.isec.server.services.auth;

import pt.isec.common.dto.auth.*;

public interface IAuthService {
    AuthResponseDTO registerTeacher(RegisterTeacherDTO registerTeacherDTO) throws Exception;
    AuthResponseDTO registerStudent(RegisterStudentDTO registerStudentDTO) throws Exception;
    AuthResponseDTO login(LoginRequestDTO dto) throws Exception;
    AuthResponseDTO updateStudent(UpdateStudentDTO dto) throws Exception;
    AuthResponseDTO updateTeacher(UpdateTeacherDTO dto) throws Exception;
    void changePassword(ChangePasswordDTO changePasswordDTO);
}
