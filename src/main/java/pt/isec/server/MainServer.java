package pt.isec.server;

import pt.isec.server.db.DbBootstrap;
import java.nio.file.Path;
public class MainServer {
    public static void main(String[] args) throws Exception {
        if (args.length != 6) {
            System.out.println("usage: MainServer <dirHost> <dirPort> <dataDir> <mcIfIp> <clientPort> <dbCopyPort>");
            return;
        }
        Class.forName("org.sqlite.JDBC");

        String dirHost = args[0];
        int dirPort = Integer.parseInt(args[1]);
        Path dataDir = java.nio.file.Paths.get(args[2]).toAbsolutePath();
        String mcIfIp = args[3];
        int clientPort = Integer.parseInt(args[4]);
        int dbCopyPort = Integer.parseInt(args[5]);

        // ficheiros diferentes por servidor (evita colisões)
        Path dbFile = dataDir.resolve("quiz-" + clientPort + ".db");

        // garante schema sempre que a BD esteja vazia/nova
        DbBootstrap.ensureSchema(dbFile);

        var node = new ServerNode(dirHost, dirPort, mcIfIp, clientPort, dbCopyPort, dbFile);
        node.start();
    }
}
