package pt.isec.server.network;

import java.net.NetworkInterface;
import java.nio.file.Path;

public interface IServerNode {
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

    boolean isPrimary();
    void setPrimary(String ip, int port);

    long dbVersion();
    void setDbVersion(long v);

    Path dbPath();

    record Principal(String ip, int port){}
}
