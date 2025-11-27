package pt.isec.server.core;
import pt.isec.server.db.DbCommands;
import pt.isec.server.services.auth.IAuthService;
import pt.isec.server.services.question.IAnswerService;
import pt.isec.server.services.question.IQuestionService;
import pt.isec.server.threads.NetworkTcpConnection;

import java.net.NetworkInterface;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.BlockingQueue;

public interface IServerManager {
    String id();
    String serverTcpIp();
    int serverTcpPort();
    int dbCopyPort();

    String directoryHost();
    int directoryPort();

    String multicastGroup();
    int multicastPort();
    NetworkInterface multicastInterface();

    boolean isRunning();
    void stopRunning(boolean v) throws Exception;

    boolean isPrimary();
    void setPrimary(String ip, int port);

    long dbVersion();
    void setDbVersion(long v);

    Path dbPath();

    void initDatabaseLayerIfNeeded();
    DbCommands getDb();
    boolean tryLockCopy();
    void unlockCopy();

    // Agora expõe apenas interfaces
    IQuestionService getQuestionService();
    IAnswerService getAnswerService();


    boolean isUserLogged(long userId);
    void registerLogin(long userId, String sessionId);
    void unregisterLogin(long userId);

    IAuthService getAuthService();

    // Gerir conexões ativas de clientes: registar / remover e enviar mensagens a um utilizador específico
    void registerClientConnection(long userId, NetworkTcpConnection conn);
    void unregisterClientConnection(long userId);
    void sendToUser(long userId, pt.isec.common.messages.TcpMessage<?> msg);

    BlockingQueue<List<String>> queue();
}
