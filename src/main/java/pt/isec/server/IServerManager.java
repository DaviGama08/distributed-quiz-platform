package pt.isec.server;

import pt.isec.server.db.DbCommands;
import pt.isec.server.services.auth.AuthService;
import pt.isec.server.services.question.AnswerService;
import pt.isec.server.services.question.QuestionService;

import java.net.NetworkInterface;
import java.nio.file.Path;
import java.util.List;

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
    void setRunning(boolean v) throws Exception;

    boolean isPrimary();
    void setPrimary(String ip, int port);

    long dbVersion();
    void setDbVersion(long v);

    Path dbPath();

    void initDatabaseLayerIfNeeded();
    DbCommands getDb();
    boolean tryLockCopy();
    void unlockCopy();

    QuestionService getQuestionService();
    AnswerService getAnswerService();
    void recordSqlUpdate(String sql);
    List<String> pollPendingSqlUpdates();

    boolean isUserLogged(long userId);
    void registerLogin(long userId, String sessionId);
    void unregisterLogin(long userId);

    AuthService getAuthService();
}
