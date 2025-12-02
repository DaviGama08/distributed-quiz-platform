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

    /* ===================== IDENTIFICAÇÃO / ENDPOINTS ===================== */

    String id();

    String serverTcpIp();
    int serverTcpPort();

    int dbCopyPort();

    String directoryHost();
    int directoryPort();


    /* ===================== MULTICAST / CLUSTER ===================== */

    String multicastGroup();
    int multicastPort();
    NetworkInterface multicastInterface();

    BlockingQueue<List<String>> queue(); // SQL a difundir para backups


    /* ===================== CICLO DE VIDA / ESTADO ===================== */

    boolean isRunning();
    void shutdownServer() throws Exception;

    boolean isPrimary();
    void setPrimary(String ip, int port);


    /* ===================== BASE DE DADOS ===================== */

    long dbVersion();
    void setDbVersion(long v);

    Path dbPath();

    void initDatabaseLayerIfNeeded();
    DbCommands getDb();

    boolean tryLockCopy();
    void unlockCopy();


    /* ===================== SERVIÇOS DE NEGÓCIO ===================== */

    IAuthService getAuthService();
    IQuestionService getQuestionService();
    IAnswerService getAnswerService();


    /* ===================== SESSÕES / LOGIN ===================== */

    boolean isUserLogged(long userId);
    void registerLogin(long userId, String sessionId);
    void unregisterLogin(long userId);


    /* ===================== CONEXÕES TCP ATIVAS ===================== */

    void registerClientConnection(long userId, NetworkTcpConnection conn);
    void unregisterClientConnection(long userId);
}
