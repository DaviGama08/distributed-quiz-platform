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
 * JavaFX main application class that bootstraps the client UI and wiring
 * with the {@link ClientManager}.
 */
public class ClientApplication extends Application {

    private static final String DIRECTORY_IP = "localhost";
    private static final int DIRECTORY_PORT = 9999;

    private AuthenticationController authController;
    private TeacherDashboardController teacherController;
    private StudentDashboardController studentController;

    /**
     * Listener for connection status changes (used to show reconnection/permanent error state).
     */
    private PropertyChangeListener connectionListener;

    private ClientManager clientManager;
    private Stage primaryStage;

    /**
     * Non-modal alert used to indicate reconnection attempts.
     */
    private Alert reconnectAlert;

    /**
     * JavaFX application entry point.
     *
     * @param stage primary application stage
     */
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

        // Allows ClientManager to close the UI when it stops
        clientManager.setUiCloser(() -> Platform.runLater(() -> {
            if (primaryStage.isShowing()) {
                primaryStage.close();
            }
            Platform.exit();
        }));

        // Listener for connection state (reconnecting / permanently disconnected)
        connectionListener = evt -> {
            String status = evt.getNewValue() == null ? "" : evt.getNewValue().toString();

            Platform.runLater(() -> {
                // Reconnecting state -> show non-modal indicator
                if (ClientService.STATUS_RECONNECTING.equals(status)) {
                    if (reconnectAlert == null) {
                        reconnectAlert = new Alert(Alert.AlertType.INFORMATION);
                        reconnectAlert.initOwner(primaryStage);
                        reconnectAlert.initModality(Modality.NONE);
                        reconnectAlert.setHeaderText(null);
                        reconnectAlert.setTitle("A tentar reconectar");

                        ProgressIndicator pi = new ProgressIndicator();
                        pi.setPrefSize(24, 24);
                        Label msg = new Label("Ligação perdida. A tentar reconectar...");
                        HBox content = new HBox(10, pi, msg);
                        content.setStyle("-fx-padding:10;");
                        reconnectAlert.getDialogPane().setContent(content);

                        reconnectAlert.show();
                    }
                    return;
                }

                // Successfully reconnected -> hide indicator
                if (ClientService.STATUS_CONNECTED.equals(status)) {
                    if (reconnectAlert != null) {
                        try {
                            reconnectAlert.close();
                        } catch (Exception ignored) {
                        }
                        reconnectAlert = null;
                    }
                    return;
                }

                // Permanent error / disconnected
                if ("DIRECTORY_ERROR".equals(status) ||
                        "SERVER_ERROR".equals(status) ||
                        "DISCONNECTED".equals(status) ||
                        ClientService.STATUS_DISCONNECTED_PERMANENT.equals(status)) {

                    if (reconnectAlert != null) {
                        try {
                            reconnectAlert.close();
                        } catch (Exception ignored) {
                        }
                        reconnectAlert = null;
                    }

                    Alert a = new Alert(Alert.AlertType.ERROR,
                            "Ligação perdida permanentemente. A aplicação vai encerrar.");
                    a.initOwner(primaryStage);
                    a.setHeaderText(null);
                    a.show();
                    // Actual closing is performed via uiCloser in ClientManager.
                }
            });
        };

        // Register connection listener
        clientManager.getService()
                .addPropertyChangeListener(ClientService.PROP_CONNECTION_STATUS, connectionListener);

        primaryStage.setTitle("Sistema de Gestão de Perguntas");

        showAuthentication();
        primaryStage.show();

        // Start the client in a separate thread
        new Thread(() -> clientManager.start()).start();
    }

    /**
     * Shows the authentication screen and clears any existing dashboard controllers.
     */
    public void showAuthentication() {
        teacherController = null;
        studentController = null;
        authController.show();
    }

    /**
     * Shows the teacher dashboard for the given user.
     *
     * @param name  teacher name
     * @param email teacher email
     */
    public void showTeacherDashboard(String name, String email) {
        teacherController = new TeacherDashboardController(primaryStage, clientManager, this, name, email);
        teacherController.show();
    }

    /**
     * Shows the student dashboard for the given user.
     *
     * @param name  student name
     * @param email student email
     */
    public void showStudentDashboard(String name, String email) {
        studentController = new StudentDashboardController(primaryStage, clientManager, this, name, email);
        studentController.show();
    }

    /**
     * Called when the JavaFX application is stopping.
     * <p>
     * Removes the connection listener and stops the {@link ClientManager}.
     */
    @Override
    public void stop() {
        if (clientManager != null) {
            if (connectionListener != null) {
                try {
                    clientManager.getService()
                            .removePropertyChangeListener(ClientService.PROP_CONNECTION_STATUS, connectionListener);
                } catch (Exception ignored) {
                }
                connectionListener = null;
            }
            clientManager.stop();
        }
    }
}
