package pt.isec.server;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class MainServer {
    public static void main(String[] args) throws Exception {
        if (args.length != 6) {
            System.out.println("usage: LauncherServer <dirHost> <dirPort> <dataDir|PROJECT|HOME> <mcIfIp|AUTO> <clientPort> <dbCopyPort>");
            return;
        }
        Class.forName("org.sqlite.JDBC");

        String dirHost    = args[0];
        int    dirPort    = Integer.parseInt(args[1]);
        String dataArg    = args[2];
        String mcIfIp     = args[3];
        int    clientPort = Integer.parseInt(args[4]);
        int    dbCopyPort = Integer.parseInt(args[5]);

        // === pasta de dados portátil ===
        Path dataDir;
        if ("PROJECT".equalsIgnoreCase(dataArg)) {
            // coloca a pasta "data" no MESMO nível de "batchFiles"
            // user.dir: é a pasta onde o comando Java foi executado, neste caso na pasta batchFiles
            Path here    = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
            Path project = here.getParent(); // uma pasta antes
            if (project == null) project = here; // fallback
            dataDir = project.resolve("data").toAbsolutePath();
        } else if ("HOME".equalsIgnoreCase(dataArg)) {
            dataDir = Paths.get(System.getProperty("user.home"), "quizdb").toAbsolutePath();
        } else {
            dataDir = Paths.get(dataArg).toAbsolutePath();
        }
        Files.createDirectories(dataDir); //Cria a pasta com o nome definido e no local definido

        // ficheiro distinto por servidor (evita colisões)
        Path dbFile = dataDir.resolve("quiz-" + clientPort + ".db");

        System.out.println("[DB] dir : " + dataDir);
        System.out.println("[DB] file: " + dbFile + " (exists=" + Files.exists(dbFile) + ")");

        // NÃO usar try-with-resources aqui, para o servidor não fechar logo
        ServerManager serverManager = new ServerManager(dirHost, dirPort, mcIfIp, clientPort, dbCopyPort, dbFile);
        serverManager.run();

        System.out.println("[LauncherServer] Servidor iniciado. Ctrl+C para terminar.");
    }
}
