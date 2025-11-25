package pt.isec.client;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.image.Image;
import javafx.scene.layout.HBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import pt.isec.client.core.ClientService;
import pt.isec.client.ui.auth.AuthenticationController;
import pt.isec.client.ui.student.StudentDashboardController;
import pt.isec.client.ui.teacher.TeacherDashboardController;

import java.beans.PropertyChangeListener;

/**
 * Classe principal JavaFX que inicia a aplicação cliente.
 */
public class ClientApplication extends Application {

    private static final String DIRECTORY_IP = "localhost";
    private static final int DIRECTORY_PORT = 9999;

    private AuthenticationController authController;
    private TeacherDashboardController teacherController;
    private StudentDashboardController studentController;

    private PropertyChangeListener connectionListener; //Para o caso de perder a ligação com o servidor, encerra a UI

    private ClientManager clientManager;
    private Stage primaryStage;

    // Non-modal reconnection indicator (keeps a single instance and is shown/hidden by connection events)
    private Alert reconnectAlert;

    @Override
    public void start(Stage stage) {
        this.primaryStage = stage;
        this.clientManager = new ClientManager(DIRECTORY_IP, DIRECTORY_PORT);

        this.authController = new AuthenticationController(primaryStage, clientManager, this);

        stage.getIcons().clear();
        var iconStream = getClass().getResourceAsStream("/imgs/app-icon.png");
        if (iconStream != null) {
            stage.getIcons().add(new Image(iconStream));
        }

        //regista o uiCloser para permitir que o manager feche a UI ao parar
        clientManager.setUiCloser(() -> {
            //garante a execução na thread JavaFX
            Platform.runLater(() -> {
                if(primaryStage.isShowing())
                    primaryStage.close();
                Platform.exit();
            });
        });

        //Regista listener para estados de erro/desconexão
        connectionListener = evt -> {
            String status = evt.getNewValue() == null ? "" : evt.getNewValue().toString();

            // Use Platform.runLater for any UI operations
            Platform.runLater(() -> {
                // Tratar estados transitórios (reconnecting) sem fechar a UI
                if (ClientService.STATUS_RECONNECTING.equals(status)) {
                    // Show a single non-modal alert with a progress indicator
                    if (reconnectAlert == null) {
                        reconnectAlert = new Alert(Alert.AlertType.INFORMATION);
                        reconnectAlert.initOwner(primaryStage);
                        reconnectAlert.initModality(Modality.NONE); // non-modal
                        reconnectAlert.setHeaderText(null);
                        reconnectAlert.setTitle("A tentar reconectar");

                        ProgressIndicator pi = new ProgressIndicator();
                        pi.setPrefSize(24, 24);
                        Label msg = new Label("Ligação perdida. A tentar reconectar...");
                        HBox content = new HBox(10, pi, msg);
                        content.setStyle("-fx-padding:10;");
                        reconnectAlert.getDialogPane().setContent(content);

                        // show non-blocking
                        reconnectAlert.show();
                    }
                    return;
                }

                // If we have reconnected, hide the reconnection indicator if visible
                if (ClientService.STATUS_CONNECTED.equals(status)) {
                    if (reconnectAlert != null) {
                        try { reconnectAlert.close(); } catch (Exception ignored) {}
                        reconnectAlert = null;
                    }
                    return;
                }

                // Fechar apenas em erro permanente
                if ("DIRECTORY_ERROR".equals(status) || "SERVER_ERROR".equals(status) || "DISCONNECTED".equals(status) || ClientService.STATUS_DISCONNECTED_PERMANENT.equals(status)) {
                    // close reconnection indicator if still open
                    if (reconnectAlert != null) {
                        try { reconnectAlert.close(); } catch (Exception ignored) {}
                        reconnectAlert = null;
                    }

                    Alert a = new Alert(Alert.AlertType.ERROR, "Ligação perdida permanentemente. A aplicação vai encerrar.");
                    a.initOwner(primaryStage);
                    a.setHeaderText(null);
                    a.show(); // mostra não bloqueante
                    // Não fechamos aqui; o uiCloser registado no ClientManager será invocado para fechar a UI de forma centralizada.
                }
            });
        };
        //Adiciona o Listener para a falha de ligação
        clientManager.getService().addPropertyChangeListener(ClientService.PROP_CONNECTION_STATUS, connectionListener);

        primaryStage.setTitle("Sistema de Gestão de Perguntas");

        showAuthentication();
        primaryStage.show();

        new Thread(() -> clientManager.start()).start();
    }


    /** Mostra o ecrã de autenticação */
    public void showAuthentication() {
        teacherController = null;
        studentController = null;
        authController.show();
    }

    public void showTeacherDashboard(String name, String email) {
        teacherController = new TeacherDashboardController(primaryStage, clientManager, this, name, email);
        teacherController.show();
    }

    public void showStudentDashboard(String name, String email) {
        studentController = new StudentDashboardController(primaryStage, clientManager, this, name, email);
        studentController.show();
    }

    @Override
    public void stop() {
        if (clientManager != null) {
            if(connectionListener != null) {
                clientManager.getService().removePropertyChangeListener(ClientService.PROP_CONNECTION_STATUS, connectionListener);
                connectionListener = null;
            }
            clientManager.stop();
        }
    }
}
