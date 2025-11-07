package pt.isec.server.services.config;

import pt.isec.common.dto.auth.LoginRequestDTO;
import pt.isec.server.services.auth.PasswordHasher;

import java.util.List;

public class ConfigServices implements IConfigServices{

    @Override
    public String generateHash(String password) throws Exception {
        return PasswordHasher.hashPassword(password);
    }

    @Override
    public String getTeachersRegisterHash(){
        return "qualquer";
    }
}
