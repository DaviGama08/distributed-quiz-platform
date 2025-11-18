package pt.isec.client.services;

import pt.isec.common.dto.auth.*;
import pt.isec.common.messages.Message;
import pt.isec.common.messages.MessageType;

import java.io.Serializable;
import java.util.concurrent.TimeUnit;

/**
 * Serviço especializado em operações de autenticação e registo.
 * Comunica com o servidor através do ClientService e interpreta as respostas
 * de acordo com o protocolo definido em MessageType.java.
 */
public class AuthClientService {

    private final ClientService service;

    public AuthClientService(ClientService service) {
        this.service = service;
    }

    /**
     * Lê mensagens da fila de respostas ignorando a mensagem ACK de handshake,
     * devolvendo apenas a próxima mensagem relevante ou null em caso de timeout.
     */
    private Message<? extends Serializable> awaitRelevantResponse(long timeoutSecs) {
        try {
            while (true) {
                Message<? extends Serializable> resp = service.waitForResponse(timeoutSecs);
                if (resp == null) {
                    return null;
                }
                // ignora ACK inicial (handshake)
                if (resp.getType() == MessageType.ACK) {
                    continue;
                }
                return resp;
            }
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Tenta efetuar login com as credenciais fornecidas.
     * Em caso de sucesso actualiza o estado interno do ClientService.
     */
    public LoginResponseDTO login(String email, String password) {
        LoginRequestDTO dto = new LoginRequestDTO(email, password);
        service.sendMessage(new Message<>(MessageType.LOGIN, dto));

        Message<?> resp = awaitRelevantResponse(5);
        if (resp == null) {
            System.err.println("[AuthClientService] Timeout à espera de resposta de login.");
            return null;
        }
        return switch (resp.getType()) {
            case LOGIN_OK -> {
                LoginResponseDTO data = resp.getDataAs(LoginResponseDTO.class);
                try {
                    service.setUserId(Integer.parseInt(data.userId()));
                } catch (NumberFormatException ignored) {
                    service.setUserId(null);
                }
                service.setUserType(data.userType());
                service.setUserEmail(data.email());
                service.setAuthenticated(true);
                yield data;
            }
            case ERROR -> {
                System.err.println("[AuthClientService] Erro de login: " + resp.getData());
                yield null;
            }
            default -> {
                System.err.println("[AuthClientService] Resposta inesperada: " + resp.getType());
                yield null;
            }
        };
    }

    /**
     * Regista um novo docente.
     * O servidor devolve LOGIN_OK com LoginResponseDTO em caso de sucesso.
     */
    public boolean registerTeacher(String name, String email,
                                   String password, String teacherCode) {
        RegisterTeacherDTO dto = new RegisterTeacherDTO(name, email, password, teacherCode);
        service.sendMessage(new Message<>(MessageType.REGISTER_TEACHER, dto));

        Message<?> resp = awaitRelevantResponse(5);
        if (resp == null) {
            System.err.println("[AuthClientService] Timeout à espera de resposta de registo docente.");
            return false;
        }
        if (resp.getType() == MessageType.LOGIN_OK) {
            LoginResponseDTO data = resp.getDataAs(LoginResponseDTO.class);
            try {
                service.setUserId(Integer.parseInt(data.userId()));
            } catch (NumberFormatException ignored) {
                service.setUserId(null);
            }
            service.setUserType(data.userType());
            service.setUserEmail(data.email());
            service.setAuthenticated(true);
            return true;
        } else {
            System.err.println("[AuthClientService] Registo docente falhou: " + resp.getData());
            return false;
        }
    }

    /**
     * Regista um novo estudante.
     * O servidor devolve LOGIN_OK com LoginResponseDTO em caso de sucesso.
     */
    public boolean registerStudent(String name, String email,
                                   String password, Integer studentNumber) {
        RegisterStudentDTO dto = new RegisterStudentDTO(name, email, password, studentNumber);
        service.sendMessage(new Message<>(MessageType.REGISTER_STUDENT, dto));

        Message<?> resp = awaitRelevantResponse(5);
        if (resp == null) {
            System.err.println("[AuthClientService] Timeout à espera de resposta de registo estudante.");
            return false;
        }
        if (resp.getType() == MessageType.LOGIN_OK) {
            LoginResponseDTO data = resp.getDataAs(LoginResponseDTO.class);
            try {
                service.setUserId(Integer.parseInt(data.userId()));
            } catch (NumberFormatException ignored) {
                service.setUserId(null);
            }
            service.setUserType(data.userType());
            service.setUserEmail(data.email());
            service.setAuthenticated(true);
            return true;
        } else {
            System.err.println("[AuthClientService] Registo estudante falhou: " + resp.getData());
            return false;
        }
    }

    /**
     * Altera a password do utilizador autenticado.
     * Esta funcionalidade não está implementada no servidor actual.
     */
    public boolean changePassword(String oldPassword, String newPassword) {
        System.err.println("[AuthClientService] Operação de alterar password não suportada.");
        return false;
    }

    /**
     * Faz logout do utilizador actual.
     */
    public boolean logout() {
        if (!service.isAuthenticated()) {
            return true;
        }
        service.sendMessage(new Message<>(MessageType.LOGOUT, null));
        Message<?> resp = awaitRelevantResponse(5);
        if (resp == null) {
            System.err.println("[AuthClientService] Timeout à espera de resposta de logout.");
            return false;
        }
        if (resp.getType() == MessageType.ACK) {
            service.setAuthenticated(false);
            service.setUserEmail(null);
            service.setUserType(null);
            service.setUserId(null);
            return true;
        } else {
            System.err.println("[AuthClientService] Logout falhou: " + resp.getData());
            return false;
        }
    }
}
