package pt.isec.server;

import pt.isec.server.core.ServerManager;
import pt.isec.common.util.Log;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class MainServer {
    public static void main(String[] args) throws Exception {
        if (args.length != 6) {
            Log.error(MainServer.class, "usage: LauncherServer <dirHost> <dirPort> <dataDir|PROJECT|HOME> <multicastIfIp|AUTO> <clientPort> <dbCopyPort>");
            return;
        }
        Class.forName("org.sqlite.JDBC");

        String dirHost    = args[0];
        int    dirPort    = Integer.parseInt(args[1]);
        String dataArg    = args[2];
        String multicastInterfaceIp     = args[3];
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

        // ficheiro distinto por servidor (evita colisões). Resolve
        Path dbFile = dataDir.resolve("quiz-" + clientPort + ".db");

        Log.info(MainServer.class, "[DB] dir : " + dataDir);
        Log.info(MainServer.class, "[DB] file: " + dbFile + " (exists=" + Files.exists(dbFile) + ")");

        // NÃO usar try-with-resources aqui, para o servidor não fechar logo
        //Cria instancia do serverManager passando IP e Porto da diretoria, MultiCast IP e porto do Client, Porto da BD e Ficheiro do BD
        ServerManager serverManager = new ServerManager(dirHost, dirPort, multicastInterfaceIp, clientPort, dbCopyPort, dbFile);

        //Apanhar o ctrl + c
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try { serverManager.stopRunning(false); } catch (Exception ignored) {}
            System.out.println("Diretoria terminada.");
        }));

        serverManager.run();

        Log.info(MainServer.class, "[LauncherServer] Servidor iniciado. Ctrl+C para terminar.");
    }
}
