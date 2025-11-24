package pt.isec.client.services;

import pt.isec.common.dto.auth.*;
import pt.isec.common.messages.TcpMessage;
import pt.isec.common.messages.MessageType;

/**
 * Serviço especializado em operações de autenticação e registo.
 * Comunica com o servidor através de IClientService e enfileira
 * mensagens para serem enviadas pela RequestSenderThread.
 *
 * Não espera pela resposta; o resultado será notificado via
 * ResponseHandlerThread que dispara os devidos eventos.
 */
public class AuthClientService {

    private final IClientService service;

    public AuthClientService(IClientService service) {
        this.service = service;
    }

    /**
     * Tenta efetuar login com as credenciais fornecidas.
     * Envia a mensagem para a fila de pedidos.
     */
    public void login(String email, String password) throws InterruptedException {
        LoginRequestDTO dto = new LoginRequestDTO(email, password);
        service.getRequestQueue().put(new TcpMessage<>(MessageType.LOGIN, dto));
    }

    /**
     * Regista um novo docente.
     */
    public void registerTeacher(String name, String email,
                                String password, String teacherCode) throws InterruptedException {
        RegisterTeacherDTO dto = new RegisterTeacherDTO(name, email, password, teacherCode);
        service.getRequestQueue().put(new TcpMessage<>(MessageType.REGISTER_TEACHER, dto));
    }

    /**
     * Regista um novo estudante.
     */
    public void registerStudent(String name, String email,
                                String password, Integer studentNumber) throws InterruptedException {
        RegisterStudentDTO dto = new RegisterStudentDTO(name, email, password, studentNumber);
        service.getRequestQueue().put(new TcpMessage<>(MessageType.REGISTER_STUDENT, dto));
    }

    /** Atualiza dados do estudante (inclui opcionalmente alteração de password) */
    public void updateStudent(UpdateStudentDTO dto) throws InterruptedException {
        if (dto == null) return;
        service.getRequestQueue().put(new TcpMessage<>(MessageType.UPDATE_STUDENT, dto));
    }

    /** Atualiza dados do docente (inclui opcionalmente alteração de password) */
    public void updateTeacher(UpdateTeacherDTO dto) throws InterruptedException {
        if (dto == null) return;
        service.getRequestQueue().put(new TcpMessage<>(MessageType.UPDATE_TEACHER, dto));
    }

    /**
     * Faz logout do utilizador actual.
     * Apenas enfileira a mensagem; quando o servidor responder com ACK,
     * o ClientService actualizará o estado de autenticação (PROP_AUTHENTICATED).
     */
    public void logout() throws InterruptedException {
        service.getRequestQueue().put(new TcpMessage<>(MessageType.LOGOUT, null));
    }
}
