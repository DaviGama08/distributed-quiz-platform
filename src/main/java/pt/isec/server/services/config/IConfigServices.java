package pt.isec.server.services.config;

import pt.isec.common.dto.auth.LoginRequestDTO;
import pt.isec.common.dto.auth.RegisterTeacherDTO;

public interface IConfigServices {
    String generateHash(String password) throws Exception;

    String getTeachersRegisterHash();

    boolean isValidPassword(String password);
    boolean isValidEmail(String email);
    boolean isValidName(String name);
}
