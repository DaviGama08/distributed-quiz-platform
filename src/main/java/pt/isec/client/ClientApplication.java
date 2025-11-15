package pt.isec.client;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.stage.Stage;
import pt.isec.client.ui.controller.AuthenticationController;

/**
 * Aplicação JavaFX principal do cliente.
 * Responsável por inicializar a interface gráfica.
 */
public class ClientApplication extends Application {

    private static final String DIRECTORY_IP = "localhost";
    private static final int DIRECTORY_PORT = 9999;

    private ClientManager clientManager;
    private Stage primaryStage;

    @Override
    public void start(Stage primaryStage) {
        this.primaryStage = primaryStage;

        // Inicializar ClientManager com IP e porta do serviço de diretoria
        this.clientManager = new ClientManager(DIRECTORY_IP, DIRECTORY_PORT);

        // Configurar janela principal
        primaryStage.setTitle("Sistema de Gestão de Perguntas");
        primaryStage.setMinWidth(900);
        primaryStage.setMinHeight(600);

        // Tratar fecho da janela
        primaryStage.setOnCloseRequest(event -> {
            event.consume(); // Prevenir fecho automático
            handleApplicationClose();
        });

        // Mostrar tela de login/autenticação
        showLoginScreen();

        primaryStage.show();
    }

    /**
     * Mostra a tela de login/autenticação.
     */
    private void showLoginScreen() {
        AuthenticationController authController =
                new AuthenticationController(primaryStage, clientManager, this);
        authController.show();
    }

    /**
     * Trata o fecho da aplicação.
     */
    private void handleApplicationClose() {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Confirmar Saída");
        alert.setHeaderText("Deseja realmente sair?");
        alert.setContentText("A conexão com o servidor será encerrada.");

        alert.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                if (clientManager != null) {
                    clientManager.stop();
                }
                Platform.exit();
                System.exit(0);
            }
        });
    }

    /**
     * Método main para lançar a aplicação JavaFX.
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
