package pt.isec.client.services;

import pt.isec.common.dto.auth.*;
import pt.isec.common.messages.Message;
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
        service.getRequestQueue().put(new Message<>(MessageType.LOGIN, dto));
    }

    /**
     * Regista um novo docente.
     */
    public void registerTeacher(String name, String email,
                                String password, String teacherCode) throws InterruptedException {
        RegisterTeacherDTO dto = new RegisterTeacherDTO(name, email, password, teacherCode);
        service.getRequestQueue().put(new Message<>(MessageType.REGISTER_TEACHER, dto));
    }

    /**
     * Regista um novo estudante.
     */
    public void registerStudent(String name, String email,
                                String password, Integer studentNumber) throws InterruptedException {
        RegisterStudentDTO dto = new RegisterStudentDTO(name, email, password, studentNumber);
        service.getRequestQueue().put(new Message<>(MessageType.REGISTER_STUDENT, dto));
    }

    /**
     * Altera a password do utilizador autenticado.
     * Esta funcionalidade ainda não é suportada no servidor.
     */
    public void changePassword(String oldPassword, String newPassword) throws InterruptedException {
        System.err.println("[AuthClientService] Operação de alterar password não suportada.");
    }

    /**
     * Faz logout do utilizador actual.
     * Apenas enfileira a mensagem; quando o servidor responder com ACK,
     * o ClientService actualizará o estado de autenticação (PROP_AUTHENTICATED).
     */
    public void logout() throws InterruptedException {
        service.getRequestQueue().put(new Message<>(MessageType.LOGOUT, null));
    }
}
