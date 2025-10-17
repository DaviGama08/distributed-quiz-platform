package pt.isec.server.services.config;

import pt.isec.common.dto.auth.LoginRequestDTO;

public class ConfigServices implements IConfigServices{

    @Override
    public String generateHash(String password) {
        return "";
    }

    @Override
    public boolean isValidPassword(String password) {
        return password != null && password.matches(".*[^a-zA-Z0-9].*]");
    }
}
