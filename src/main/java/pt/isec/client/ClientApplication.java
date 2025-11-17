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

        // Cria o controlador de autenticação
        this.authController = new AuthenticationController(primaryStage, clientManager, this);

        stage.getIcons().clear();
        stage.getIcons().add(new Image(Objects.requireNonNull(getClass().getResourceAsStream("/imgs/app-icon.png"))));

        primaryStage.setTitle("Sistema de Gestão de Perguntas");
        showAuthentication();
        primaryStage.show();
    }

    /** Mostra o ecrã de autenticação */
    public void showAuthentication() {
        teacherController = null;
        studentController = null;
        authController.show();
    }

    /** Abre o dashboard do docente */
    public void showTeacherDashboard(String email) {
        teacherController = new TeacherDashboardController(primaryStage, clientManager, this, email);
        teacherController.show();
    }

    /** Abre o dashboard do estudante */
    public void showStudentDashboard(String email) {
        studentController = new StudentDashboardController(primaryStage, clientManager, this, email);
        studentController.show();
    }

    @Override
    public void stop() {
        if (clientManager != null) {
            clientManager.stop();
        }
    }
}
