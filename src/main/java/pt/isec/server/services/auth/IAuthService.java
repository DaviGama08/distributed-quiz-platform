package pt.isec.server.services.auth;

import pt.isec.common.dto.auth.*;

/**
 * Authentication and profile management service contract.
 */
public interface IAuthService {

    /* ===================== REGISTRATION ===================== */

    /**
     * Registers a new teacher account.
     *
     * @param registerTeacherDTO teacher registration data
     * @return authentication response with session and user info
     * @throws Exception if validation or database access fails
     */
    AuthResponseDTO registerTeacher(RegisterTeacherDTO registerTeacherDTO) throws Exception;

    /**
     * Registers a new student account.
     *
     * @param registerStudentDTO student registration data
     * @return authentication response with session and user info
     * @throws Exception if validation or database access fails
     */
    AuthResponseDTO registerStudent(RegisterStudentDTO registerStudentDTO) throws Exception;


    /* ===================== AUTHENTICATION ===================== */

    /**
     * Authenticates a user (teacher or student) by email and password.
     *
     * @param dto login request data
     * @return authentication response with session and user info
     * @throws Exception if validation or database access fails
     */
    AuthResponseDTO login(LoginRequestDTO dto) throws Exception;


    /* ===================== PROFILE / CHANGES ===================== */

    /**
     * Updates a student profile (name, email, student number and optionally password).
     *
     * @param dto profile update data
     * @return updated authentication response (without changing session id)
     * @throws Exception if validation or database access fails
     */
    AuthResponseDTO updateStudent(UpdateStudentDTO dto) throws Exception;

    /**
     * Updates a teacher profile (name, email and optionally password).
     *
     * @param dto profile update data
     * @return updated authentication response (without changing session id)
     * @throws Exception if validation or database access fails
     */
    AuthResponseDTO updateTeacher(UpdateTeacherDTO dto) throws Exception;

    /**
     * Changes the password for an existing user.
     *
     * @param changePasswordDTO password change data
     */
    void changePassword(ChangePasswordDTO changePasswordDTO);
}
