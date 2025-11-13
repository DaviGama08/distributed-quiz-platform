package pt.isec.client;


public class MainClient {
    public static void main(String[] args) {
        if(args.length < 2){
            throw new IllegalArgumentException("Número de argumentos inválido");
        }

        System.out.printf("=== Cliente ===%nUDP: " + args[0] + ":" + args[1]);

        try {
            ClientManager manager = new ClientManager(args[0], Integer.parseInt(args[1]));

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try { manager.stop(); } catch (Exception ignored) {}
                System.out.println("Cliente encerrado");
            }));

            manager.start();
            System.out.println("Cliente a correr. CTRL+C para sair.");
        } catch (Exception e) {
            System.err.println("Falha ao iniciar o cliente: " + e.getMessage());
            e.printStackTrace();
        }
    }
}

