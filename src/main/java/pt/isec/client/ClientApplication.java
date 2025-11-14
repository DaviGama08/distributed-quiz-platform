package pt.isec.client;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.stage.Stage;
import pt.isec.client.screen.AuthenticationScreen;

/**
 * Aplicação JavaFX principal do cliente
 * Responsável por inicializar a interface gráfica
 */
public class ClientApplication extends Application {

    private ClientManager clientManager;
    private Stage primaryStage;

    @Override
    public void start(Stage primaryStage) {
        this.primaryStage = primaryStage;

        // Configurar janela principal
        primaryStage.setTitle("Sistema de Gestão de Perguntas");
        primaryStage.setMinWidth(900);
        primaryStage.setMinHeight(600);

        // Tratar fecho da janela
        primaryStage.setOnCloseRequest(event -> {
            event.consume(); // Prevenir fecho automático
            handleApplicationClose();
        });

        // Mostrar tela de login
        showLoginScreen();

        primaryStage.show();
    }

    /**
     * Mostra a tela de login/autenticação
     */
    private void showLoginScreen() {
        // Obter IP e porta do serviço de diretoria dos parâmetros
        Parameters params = getParameters();
        java.util.List<String> args = params.getRaw();

        String directoryIP = !args.isEmpty() ? args.get(0) : "localhost";
        int directoryPort = args.size() > 1 ? Integer.parseInt(args.get(1)) : 9000;

        // Criar ClientManager
        this.clientManager = new ClientManager(directoryIP, directoryPort);

        // Mostrar tela de autenticação
        AuthenticationScreen authScreen = new AuthenticationScreen(primaryStage, clientManager, this);
        authScreen.show();
    }

    /**
     * Trata o fecho da aplicação
     */
    private void handleApplicationClose() {
        javafx.scene.control.Alert alert = new javafx.scene.control.Alert(
                javafx.scene.control.Alert.AlertType.CONFIRMATION
        );
        alert.setTitle("Confirmar Saída");
        alert.setHeaderText("Deseja realmente sair?");
        alert.setContentText("A conexão com o servidor será encerrada.");

        alert.showAndWait().ifPresent(response -> {
            if (response == javafx.scene.control.ButtonType.OK) {
                if (clientManager != null) {
                    clientManager.stop();
                }
                Platform.exit();
                System.exit(0);
            }
        });
    }

    /**
     * Método main para lançar a aplicação JavaFX
     */
    public static void launchApp(String[] args) {
        launch(args);
    }

    @Override
    public void stop() {
        if (clientManager != null) {
            clientManager.stop();
        }
    }
}
