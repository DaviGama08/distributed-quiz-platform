package pt.isec.directory;

public class LauncherDirectory {
    // Defaults
    private static final int  DEF_UDP_PORT     = 9999;
    private static final int  DEF_QUEUE        = 1024;
    private static final int  DEF_MAX_PKT      = 65535;
    private static final long DEF_TTL_MS       = 17_000L;

    public static void main(String[] args) {
        int  udpPort       = DEF_UDP_PORT;
        int  queueCapacity = DEF_QUEUE;
        int  maxPacketSize = DEF_MAX_PKT;
        long ttlMillis     = DEF_TTL_MS;

        try {
            switch (args.length) {
                case 0 -> { /* tudo por defeito */ }
                case 1 -> udpPort       = parseIntOr(args[0], DEF_UDP_PORT);
                case 4 -> {
                    udpPort       = parseIntOr(args[0], DEF_UDP_PORT);
                    queueCapacity = parseIntOr(args[1], DEF_QUEUE);
                    maxPacketSize = parseIntOr(args[2], DEF_MAX_PKT);
                    ttlMillis     = parseLongOr(args[3], DEF_TTL_MS);
                }
                default -> {
                    System.out.println("""
                        Uso:
                          java MainDirectory
                          java MainDirectory <udpPort>
                          java MainDirectory <udpPort> <queueCapacity> <maxPacketSize> <ttlMillis>
                        """.trim());
                    System.out.println("(Argumentos inválidos: a iniciar com valores por defeito)");
                }
            }

            // Validações leves (se inválido, volta ao default)
            udpPort       = (udpPort >= 1 && udpPort <= 65535) ? udpPort : DEF_UDP_PORT;
            queueCapacity = (queueCapacity > 0) ? queueCapacity : DEF_QUEUE;
            // 65535 é o máximo do UDP; aceita algo razoável (>=512) para evitar pacotes minúsculos
            maxPacketSize = (maxPacketSize >= 512 && maxPacketSize <= 65535) ? maxPacketSize : DEF_MAX_PKT;
            ttlMillis     = (ttlMillis > 0) ? ttlMillis : DEF_TTL_MS;

            System.out.printf(
                    "=== Diretoria ===%nUDP:%d | queue:%d | maxPkt:%d | TTL(ms):%d%n",
                    udpPort, queueCapacity, maxPacketSize, ttlMillis
            );

            DirectoryService ds = new DirectoryService(udpPort, queueCapacity, maxPacketSize);
            // Se o teu DirectoryService suportar TTL, adiciona-o no construtor ou como setter aqui.

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try { ds.stop(); } catch (Exception ignored) {}
                System.out.println("Diretoria terminada.");
            }));

            ds.start();
            System.out.println("Diretoria a correr. CTRL+C para sair.");
        } catch (Exception e) {
            System.err.println("Falha ao iniciar a diretoria: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static int parseIntOr(String s, int def) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return def; }
    }
    private static long parseLongOr(String s, long def) {
        try { return Long.parseLong(s.trim()); } catch (Exception e) { return def; }
    }
}
