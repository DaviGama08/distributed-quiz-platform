package pt.isec.server.threads;

import pt.isec.common.dto.auth.LoginRequestDTO;
import pt.isec.common.dto.auth.LoginResponseDTO;
import pt.isec.common.dto.auth.RegisterStudentDTO;
import pt.isec.common.dto.auth.RegisterTeacherDTO;
import pt.isec.common.messages.MessageType;
import pt.isec.common.messages.Message;
import pt.isec.server.IQuizServer;
import pt.isec.server.NetworkConnection;

import java.io.IOException;
import java.time.Duration;

public class ClientHandlerThread implements Runnable{
    private static final int FIRST_MESSAGE_TIMEOUT_SEC = 30;
    private static final Duration NO_TIMEOUT = Duration.ZERO;

    private final IQuizServer tInfo;
    private NetworkConnection connection;

    public ClientHandlerThread(IQuizServer tInfo, NetworkConnection connection) {
        this.tInfo = tInfo; this.connection = connection;
    }

    @Override
    public void run() {
        try {
            // o cliente deve enviar a 1ª mensagem em até 30 segundos
            connection.setReadTimeout(Duration.ofSeconds(FIRST_MESSAGE_TIMEOUT_SEC));

            // se o servidor não for primário, rejeita o cliente
            if (!tInfo.isPrimary()) {
                connection.sendMessage(new Message<>(MessageType.NACK, "not-primary"));
                return;
            }

            // caso contrário, confirma ligação
            connection.sendMessage(new Message<>(MessageType.ACK, "ok"));

            // remove timeout depois da primeira interação
            connection.setReadTimeout(NO_TIMEOUT);

            // loop principal da sessão do cliente
            while (tInfo.isRunning()) {
                Message<?> msg = connection.receiveMessage();
                if (msg == null)
                    break;

                // agora o processMessage já trata de enviar a resposta
                processMessage(msg);
            }

        } catch (Exception ignore) {
            // falha silenciosa — o cliente pode ter fechado a ligação
        } finally {
            // garante que o socket é fechado corretamente
            try {
                if (connection != null) connection.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private void processMessage(Message<?> message) throws Exception {
        if (message == null)
            return;

        //TODO: falta implementar o resto das mensagens
        switch (message.getType()) {
            //Autenticação
            case REGISTER_STUDENT -> {
                try {
                    //Faz a conversão do genérico T para o RegisterStudentDTO, já que em runtime não sabemos
                    //qual o tipo do genérico em si, só sabemos que ele é do tipo T, com momento de compilação que
                    //é preciso para comparar os tipos.
                    RegisterStudentDTO dto = message.getDataAs(RegisterStudentDTO.class);
                    LoginResponseDTO res   = tInfo.getAuthService().registerStudent(dto);

                    connection.sendMessage(new Message<>(MessageType.LOGIN_OK, res, LoginResponseDTO.class));
                } catch (ClassCastException e) {
                    //Se passarmos um type de Message diferente do que está no data, é lançada essa exceção, já
                    //que tentaremos fazer o cast de um genérico para um Objeto que não corresponde com a
                    //o payload que passamos, nesse caso, RegisterStudentDTO
                    connection.sendMessage(new Message<>(MessageType.ERROR,
                            "Tipo de payload inválido para REGISTER_STUDENT", String.class));
                } catch (Exception e) {
                    connection.sendMessage(new Message<>(MessageType.ERROR, e.getMessage(), String.class));
                }
            }
            case REGISTER_TEACHER -> {
                try {
                    RegisterTeacherDTO dto = message.getDataAs(RegisterTeacherDTO.class);
                    LoginResponseDTO   res = tInfo.getAuthService().registerTeacher(dto);

                    connection.sendMessage(new Message<>(MessageType.LOGIN_OK, res, LoginResponseDTO.class));
                } catch (ClassCastException e) {
                    connection.sendMessage(new Message<>(MessageType.ERROR,
                            "Tipo de payload inválido para REGISTER_TEACHER", String.class));
                } catch (Exception e) {
                    connection.sendMessage(new Message<>(MessageType.ERROR, e.getMessage(), String.class));
                }
            }
            case LOGIN -> {
                try {
                    LoginRequestDTO   dto = message.getDataAs(LoginRequestDTO.class);
                    LoginResponseDTO  res = tInfo.getAuthService().login(dto);

                    connection.sendMessage(new Message<>(MessageType.LOGIN_OK, res, LoginResponseDTO.class));
                } catch (ClassCastException e) {
                    connection.sendMessage(new Message<>(MessageType.ERROR,
                            "Tipo de payload inválido para LOGIN", String.class));
                } catch (Exception e) {
                    connection.sendMessage(new Message<>(MessageType.ERROR, e.getMessage(), String.class));
                }
            }
            case LOGOUT -> {
                try {
                    //TODO: Por fazer
                } catch (ClassCastException e) {
                    // opcional: enviar mensagem de erro específica
                    connection.sendMessage(new Message<>(MessageType.ERROR,
                            "Tipo de payload inválido para LOGOUT", String.class));
                } catch (Exception e) {
                    connection.sendMessage(new Message<>(MessageType.ERROR, e.getMessage(), String.class));
                }
            }
            //TODO: Funcionalidades dos docentes
            //...
            //TODO: Funcionalidades dos alunos
        }
    }
}
