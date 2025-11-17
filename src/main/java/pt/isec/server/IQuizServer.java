package pt.isec.server;

import pt.isec.server.db.Db;
import pt.isec.server.services.auth.AuthService;

import java.net.NetworkInterface;
import java.nio.file.Path;

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

    AuthService getAuthService();
}
