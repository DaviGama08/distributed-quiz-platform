package pt.isec;

import pt.isec.directory.DirectoryService;

public class MainDirectory {
    /*
      Uso:
        java pt.isec.directory.MainDirectory [udpPort] [queueCapacity] [maxPacketSize] [ttlMillis]
      Defaults:
        udpPort=9999 | queue=1024 | maxPacket=65535 | ttl=17000
    */
    public static void main(String[] args) {
        int udpPort       = argInt(args, 0, 9999);
        int queueCapacity = argInt(args, 1, 1024);
        int maxPacketSize = argInt(args, 2, 65535);
        long ttlMillis    = argLong(args, 3, 17_000L);

        System.out.printf("=== Diretoria ===%nUDP:%d | queue:%d | maxPkt:%d | TTL(ms):%d%n",
                udpPort, queueCapacity, maxPacketSize, ttlMillis);

        try {
            DirectoryService ds = new DirectoryService(udpPort, queueCapacity, maxPacketSize);

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

    private static int argInt(String[] a, int i, int def){ if(a.length<=i) return def; try{return Integer.parseInt(a[i]);}catch(Exception e){return def;}}
    private static long argLong(String[] a, int i, long def){ if(a.length<=i) return def; try{return Long.parseLong(a[i]);}catch(Exception e){return def;}}
}
