package pt.isec.client.services;

import pt.isec.common.dto.auth.*;
import pt.isec.common.messages.Message;
import pt.isec.common.messages.MessageType;

/**
 * Serviço de autenticação do cliente
 * Fornece métodos de alto nível para registro e login
 */
public class AuthClientService {
    private final ClientService clientService;
    private AuthenticatedUserDTO currentUser;

    public AuthClientService(ClientService clientService) {
        this.clientService = clientService;
    }

    /**
     * Registra um novo estudante
     */
    public boolean registerStudent(String name, String email, String password, Integer studentNumber) {
        RegisterStudentDTO dto = new RegisterStudentDTO(name, email, password, studentNumber);
        Message<RegisterStudentDTO> message = new Message<>(MessageType.REGISTER_STUDENT, dto);

        System.out.println("[AuthClient] Registering student: " + email);
        clientService.sendMessage(message);

        try {
            Message<?> response = clientService.waitForResponse();
            if(response.getType() == MessageType.ACK) {
                System.out.println("[AuthClient] Student registered successfully");
                return true;
            } else {
                System.err.println("[AuthClient] Registration failed: " + response.getType());
                return false;
            }
        } catch (InterruptedException e) {
            System.err.println("[AuthClient] Registration interrupted: " + e.getMessage());
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * Registra um novo professor
     */
    public boolean registerTeacher(String name, String email, String password, String teacherCode) {
        RegisterTeacherDTO dto = new RegisterTeacherDTO(name, email, password, teacherCode);
        Message<RegisterTeacherDTO> message = new Message<>(MessageType.REGISTER_TEACHER, dto);

        System.out.println("[AuthClient] Registering teacher: " + email);
        clientService.sendMessage(message);

        try {
            Message<?> response = clientService.waitForResponse();
            if(response.getType() == MessageType.ACK) {
                System.out.println("[AuthClient] Teacher registered successfully");
                return true;
            } else {
                System.err.println("[AuthClient] Registration failed: " + response.getType());
                return false;
            }
        } catch (InterruptedException e) {
            System.err.println("[AuthClient] Registration interrupted: " + e.getMessage());
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * Faz login de um usuário
     */
    public LoginResponseDTO login(String email, String password) {
        LoginRequestDTO dto = new LoginRequestDTO(email, password);
        Message<LoginRequestDTO> message = new Message<>(MessageType.LOGIN, dto);

        System.out.println("[AuthClient] Logging in: " + email);
        clientService.sendMessage(message);

        try {
            Message<?> response = clientService.waitForResponse();
            if(response.getType() == MessageType.LOGIN_OK) {
                LoginResponseDTO loginData = response.getDataAs(LoginResponseDTO.class);

                // Salvar usuário autenticado
                this.currentUser = new AuthenticatedUserDTO(
                    loginData.userId(),
                    loginData.name(),
                    loginData.email(),
                    loginData.userType()
                );

                System.out.println("[AuthClient] Login successful: " + loginData.name() + " (" + loginData.userType() + ")");
                return loginData;
            } else {
                System.err.println("[AuthClient] Login failed: " + response.getType());
                return null;
            }
        } catch (InterruptedException e) {
            System.err.println("[AuthClient] Login interrupted: " + e.getMessage());
            Thread.currentThread().interrupt();
            return null;
        }
    }

    /**
     * Faz logout do usuário atual
     */
    public boolean logout() {
        if(currentUser == null) {
            System.err.println("[AuthClient] No user logged in");
            return false;
        }

        Message<String> message = new Message<>(MessageType.LOGOUT, currentUser.id());
        clientService.sendMessage(message);

        currentUser = null;
        System.out.println("[AuthClient] Logged out successfully");
        return true;
    }

    /**
     * Altera a senha do usuário atual
     */
    public boolean changePassword(String oldPassword, String newPassword) {
        if(currentUser == null) {
            System.err.println("[AuthClient] No user logged in");
            return false;
        }

        try {
            Integer sessionId = Integer.parseInt(currentUser.id());
            ChangePasswordDTO dto = new ChangePasswordDTO(sessionId, oldPassword, newPassword);
            Message<ChangePasswordDTO> message = new Message<>(MessageType.REGISTER_STUDENT, dto); // TODO: Add CHANGE_PASSWORD type

            clientService.sendMessage(message);

            Message<?> response = clientService.waitForResponse();
            return response.getType() == MessageType.ACK;
        } catch (NumberFormatException | InterruptedException e) {
            System.err.println("[AuthClient] Failed to change password: " + e.getMessage());
            return false;
        }
    }

    public AuthenticatedUserDTO getCurrentUser() {
        return currentUser;
    }

    public boolean isLoggedIn() {
        return currentUser != null;
    }

    public boolean isTeacher() {
        return currentUser != null && "teacher".equalsIgnoreCase(currentUser.userType());
    }

    public boolean isStudent() {
        return currentUser != null && "student".equalsIgnoreCase(currentUser.userType());
    }
}

