package pt.isec.client;
import javafx.application.Application;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import pt.isec.client.ui.controller.AuthenticationController;
import pt.isec.client.ui.controller.StudentDashboardController;
import pt.isec.client.ui.controller.TeacherDashboardController;

import java.util.Objects;

/**
 * Classe principal JavaFX que inicia a aplicação cliente.
 */
public class ClientApplication extends Application {

    private static final String DIRECTORY_IP = "localhost";
    private static final int DIRECTORY_PORT = 9999;

    private AuthenticationController authController;
    private TeacherDashboardController teacherController;
    private StudentDashboardController studentController;

    private ClientManager clientManager;
    private Stage primaryStage;

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
            clientManager.stop();
        }
    }
}
