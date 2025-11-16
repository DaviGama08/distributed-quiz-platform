package pt.isec.server.services.config;

import pt.isec.server.services.auth.helpers.PasswordHasher;

public class ConfigServices implements IConfigServices {

    // Código de registo que os docentes devem usar no formulário
    // (campo "Código de Docente" no cliente).
    private static final String TEACHER_REGISTER_CODE = "DOCENTE2025";

    @Override
    public String generateHash(String password) throws Exception {
        return PasswordHasher.hashPassword(password);
    }

    @Override
    public String getTeachersRegisterHash() {
        // Neste design devolvemos o código em claro, não um hash.
        // O AuthService faz a comparação direta com o código introduzido.
        return TEACHER_REGISTER_CODE;
    }
}
