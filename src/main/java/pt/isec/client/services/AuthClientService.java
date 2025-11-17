package pt.isec.client.services;

import pt.isec.common.dto.auth.LoginRequestDTO;
import pt.isec.common.dto.auth.LoginResponseDTO;
import pt.isec.common.dto.auth.RegisterStudentDTO;
import pt.isec.common.dto.auth.RegisterTeacherDTO;
import pt.isec.common.messages.Message;
import pt.isec.common.messages.MessageType;

import java.io.Serializable;

/**
 * Serviço responsável pelas operações de autenticação (login e registo).
 */
public class AuthClientService {

    private static final long REQ_TIMEOUT_MS = 8000; // 8 segundos

    private final ClientService service;

    public AuthClientService(ClientService service) {
        this.service = service;
    }

    /** Envia pedido de login e aguarda resposta com timeout */
    public LoginResponseDTO login(String email, String password) throws Exception {
        LoginRequestDTO dto = new LoginRequestDTO(email, password);
        Message<LoginRequestDTO> msg =
                new Message<>(MessageType.LOGIN, dto, LoginRequestDTO.class);

        service.sendMessage(msg);

        Message<? extends Serializable> resp =
                service.waitForResponse(REQ_TIMEOUT_MS);

        if (resp == null) {
            throw new RuntimeException("Timeout ao aguardar resposta do servidor.");
        }

        return switch (resp.getType()) {
            case LOGIN_OK -> resp.getDataAs(LoginResponseDTO.class);
            case LOGIN_FAIL -> null; // credenciais inválidas
            case ERROR -> throw new RuntimeException(String.valueOf(resp.getData()));
            default -> throw new RuntimeException("Resposta inesperada: " + resp.getType());
        };
    }

    /** Regista um docente */
    public boolean registerTeacher(String name, String email,
                                   String password, String teacherCode) throws Exception {

        RegisterTeacherDTO dto = new RegisterTeacherDTO(name, email, password, teacherCode);
        Message<RegisterTeacherDTO> msg =
                new Message<>(MessageType.REGISTER_TEACHER, dto, RegisterTeacherDTO.class);

        service.sendMessage(msg);

        Message<? extends Serializable> resp =
                service.waitForResponse(REQ_TIMEOUT_MS);

        if (resp == null) {
            throw new RuntimeException("Timeout ao aguardar resposta do servidor.");
        }

        return switch (resp.getType()) {
            case LOGIN_OK, ACK -> true;              // registo OK
            case ERROR, NACK -> {
                throw new RuntimeException(String.valueOf(resp.getData()));
            }
            default -> false;
        };
    }

    /** Regista um estudante */
    public boolean registerStudent(String name, String email,
                                   String password, int studentNumber) throws Exception {

        RegisterStudentDTO dto = new RegisterStudentDTO(name, email, password, studentNumber);
        Message<RegisterStudentDTO> msg =
                new Message<>(MessageType.REGISTER_STUDENT, dto, RegisterStudentDTO.class);

        service.sendMessage(msg);

        Message<? extends Serializable> resp =
                service.waitForResponse(REQ_TIMEOUT_MS);

        if (resp == null) {
            throw new RuntimeException("Timeout ao aguardar resposta do servidor.");
        }

        return switch (resp.getType()) {
            case LOGIN_OK, ACK -> true;
            case ERROR, NACK -> {
                throw new RuntimeException(String.valueOf(resp.getData()));
            }
            default -> false;
        };
    }
}
