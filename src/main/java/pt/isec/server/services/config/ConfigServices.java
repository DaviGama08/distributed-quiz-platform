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


    @Override
    public boolean isValidPassword(String password) {
        return password != null && password.matches(".*[^a-zA-Z0-9].*]");
    }

    @Override
    public boolean isValidEmail(String email){
        if (email == null) return false;
        String regex = "^[A-Za-z0-9._%+-]+@(?:isec|gmail|hotmail|outlook|yahoo)\\.[A-Za-z]{2,}$";
        return email.matches(regex);
    }

    @Override
    public boolean isValidName(String name) {
        if (name == null || name.isBlank()) return false;
        String regex = "^[A-Za-zÀ-ÖØ-öø-ÿ]+(?: [A-Za-zÀ-ÖØ-öø-ÿ]+)*$";
        return name.matches(regex);
    }
}
