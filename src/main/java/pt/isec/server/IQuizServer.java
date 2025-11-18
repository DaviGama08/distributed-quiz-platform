package pt.isec.server;

import pt.isec.server.db.Db;
import pt.isec.server.services.auth.AuthService;
import pt.isec.server.services.question.AnswerService;
import pt.isec.server.services.question.QuestionService;

import java.net.NetworkInterface;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public interface IQuizServer {
    String id();
    String ip();
    int clientPort();
    int dbCopyPort();

    String directoryHost();
    int directoryPort();

    String mcGroup();
    int mcPort();
    NetworkInterface mcIf();

    boolean isRunning();
    void setRunning(boolean v) throws Exception;

    boolean isPrimary();
    void setPrimary(String ip, int port);

    long dbVersion();
    void setDbVersion(long v);

    Path dbPath();

    void initDatabaseLayerIfNeeded();
    Db getDb();
    boolean tryLockCopy();
    void unlockCopy();

    QuestionService getQuestionService();
    AnswerService getAnswerService();
    void recordSqlUpdate(String sql);
    List<String> pollPendingSqlUpdates();

    AuthService getAuthService();
}
