package pt.isec.client.ui.student;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

public class StudentDashboardView {
    private String userName;
    private final String userEmail;
    private Scene scene;

    private Label profileNameLabel;
    private Label welcomeLabel;
    private Label headerEmailLabel;
    private TextArea notificationArea;

    private Button answerQuestionBtn;
    private Button historyBtn;
    private Button logoutBtn;

    private VBox profileCard;
    private HBox loadingBox;

    public StudentDashboardView(String userName, String userEmail) {
        this.userName = userName;
        this.userEmail = userEmail;
    }

    public void createView() {
        BorderPane root = new BorderPane();
        root.getStyleClass().add("dashboard-root-dark");

        root.setTop(createHeader());
        root.setLeft(createSidebar());
        root.setCenter(createMainArea());

        scene = new Scene(root, 1000, 700);
        try {
            var cssUrl = getClass().getResource("/styles/dashboard.css");
            if (cssUrl != null) {
                scene.getStylesheets().add(cssUrl.toExternalForm());
            }
        } catch (Exception ignored) { }
    }

    private VBox createHeader() {
        VBox header = new VBox(10);
        header.getStyleClass().add("dashboard-header-dark");

        welcomeLabel = new Label("Bem-vindo, " +
                (userName != null ? userName : "Estudante") + "!");
        welcomeLabel.setFont(Font.font("Arial", FontWeight.NORMAL, 24));
        welcomeLabel.getStyleClass().add("header-welcome");

        headerEmailLabel = new Label(userEmail);
        headerEmailLabel.getStyleClass().add("header-email-dark");

        loadingBox = new HBox(8);
        loadingBox.setAlignment(Pos.CENTER_LEFT);
        loadingBox.setPadding(new Insets(4, 0, 0, 0));
        ProgressIndicator pi = new ProgressIndicator();
        pi.setPrefSize(16, 16);
        Label loadingLabel = new Label("");
        loadingLabel.setStyle("-fx-text-fill: #7f8c8d;");
        loadingBox.getChildren().addAll(pi, loadingLabel);
        loadingBox.setVisible(false);

        header.getChildren().addAll(welcomeLabel, headerEmailLabel, loadingBox);
        return header;
    }

    public void showLoading(String message) {
        if (loadingBox == null) return;
        Platform.runLater(() -> {
            Label label = (Label) loadingBox.getChildren().get(1);
            label.setText(message);
            loadingBox.setVisible(true);
        });
    }

    public void hideLoading() {
        if (loadingBox == null) return;
        Platform.runLater(() -> loadingBox.setVisible(false));
    }

    private VBox createSidebar() {
        VBox sidebar = new VBox(12);
        sidebar.getStyleClass().add("dashboard-sidebar-dark");
        sidebar.setPrefWidth(240);

        profileCard = createProfileCard();

        Label menuLabel = new Label("MENU");
        menuLabel.getStyleClass().add("sidebar-title-dark");

        answerQuestionBtn = createMenuButton("Responder Pergunta");
        historyBtn = createMenuButton("Histórico");
        logoutBtn = createMenuButton("Logout");

        sidebar.getChildren().addAll(
                menuLabel,
                new Separator(),
                profileCard,
                answerQuestionBtn,
                historyBtn,
                logoutBtn
        );

        return sidebar;
    }

    private VBox createProfileCard() {
        VBox card = new VBox(8);
        card.getStyleClass().add("profile-card");
        card.setAlignment(Pos.CENTER);

        StackPane avatarCircle = new StackPane();
        avatarCircle.getStyleClass().add("profile-avatar-circle");

        Label initials = new Label(getInitials(userName != null ? userName : userEmail));
        initials.getStyleClass().add("profile-avatar-initials");
        avatarCircle.getChildren().add(initials);

        profileNameLabel = new Label(userName != null ? userName : "Utilizador");
        profileNameLabel.getStyleClass().add("profile-name-label");

        Label roleLabel = new Label("Estudante");
        roleLabel.getStyleClass().add("profile-role-label");

        card.getChildren().addAll(avatarCircle, profileNameLabel, roleLabel);
        return card;
    }

    private String getInitials(String text) {
        if (text == null || text.isBlank()) return "?";
        String[] parts = text.trim().split("\\s+");
        if (parts.length == 1)
            return parts[0].substring(0, 1).toUpperCase();
        return ("" + parts[0].charAt(0) + parts[parts.length - 1].charAt(0)).toUpperCase();
    }

    public void setProfileName(String name) {
        if (name == null || name.isBlank())
            return;
        this.userName = name;
        if (profileNameLabel != null)
            profileNameLabel.setText(name);
        if (welcomeLabel != null)
            welcomeLabel.setText("Bem-vindo, " + name + "!");
    }

    public String getProfileName() {
        return profileNameLabel != null ? profileNameLabel.getText() : userName;
    }

    private VBox createMainArea() {
        VBox mainArea = new VBox(20);
        mainArea.getStyleClass().add("dashboard-main-area");
        mainArea.setPadding(new Insets(20));

        Label notifLabel = new Label("Notificações");
        notifLabel.setFont(Font.font("Arial", FontWeight.NORMAL, 18));
        notifLabel.getStyleClass().add("dashboard-section-title");

        notificationArea = new TextArea();
        notificationArea.setEditable(false);
        notificationArea.setPrefHeight(150);
        notificationArea.setPromptText("Aguardando notificações do servidor...");
        notificationArea.getStyleClass().add("notification-area-dark");
        notificationArea.setWrapText(true);

        Label instructionsLabel = new Label("Como começar:");
        instructionsLabel.setFont(Font.font("Arial", FontWeight.NORMAL, 16));
        instructionsLabel.getStyleClass().add("dashboard-section-title");

        VBox instructions = new VBox(10);
        instructions.getStyleClass().add("instructions-box-dark");
        instructions.setAlignment(Pos.TOP_LEFT);

        Label inst1 = new Label("1. Obtenha o código da pergunta com o seu docente");
        Label inst2 = new Label("2. Clique em 'Responder Pergunta' no menu lateral");
        Label inst3 = new Label("3. Insira o código e responda à pergunta");
        Label inst4 = new Label("4. Verifique seu histórico de respostas a qualquer momento");

        inst1.getStyleClass().add("instruction-line");
        inst2.getStyleClass().add("instruction-line");
        inst3.getStyleClass().add("instruction-line");
        inst4.getStyleClass().add("instruction-line");

        instructions.getChildren().addAll(inst1, inst2, inst3, inst4);

        mainArea.getChildren().addAll(
                notifLabel,
                notificationArea,
                instructionsLabel,
                instructions
        );
        return mainArea;
    }

    private Button createMenuButton(String text) {
        Button btn = new Button(text);
        btn.getStyleClass().add("sidebar-button-dark");
        btn.setPrefWidth(Double.MAX_VALUE);
        return btn;
    }

    public Scene getScene() {
        return scene;
    }

    public void setWelcomeName(String name) {
        if (name == null || name.trim().isEmpty()) {
            welcomeLabel.setText("Bem-vindo, Estudante!");
        } else {
            welcomeLabel.setText("Bem-vindo, " + name + "!");
        }
    }

    public void registerHandlers(StudentDashboardController controller) {
        answerQuestionBtn.setOnAction(e -> controller.onAnswerQuestion());
        historyBtn.setOnAction(e -> controller.onShowHistory());
        logoutBtn.setOnAction(e -> controller.onLogout());

        if (profileCard != null) {
            profileCard.setOnMouseClicked(e -> controller.onProfile());
        }
    }

    public void update() {
        // nada extra por agora
    }

    public void addNotification(String message) {
        if (notificationArea == null) return;
        String timestamp = java.time.LocalTime.now().format(
                java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss")
        );
        notificationArea.appendText("[" + timestamp + "] " + message + "\n");
    }

    /** Atualiza nome/email em todo o dashboard (header + perfil) */
    public void updateUserInfo(String name, String email) {
        this.userName = name;
        if (profileNameLabel != null && name != null && !name.isBlank()) {
            profileNameLabel.setText(name);
        }
        if (welcomeLabel != null) {
            setWelcomeName(name);
        }
        if (headerEmailLabel != null && email != null && !email.isBlank()) {
            headerEmailLabel.setText(email);
        }

        // atualizar iniciais do avatar
        if (profileCard != null && !profileCard.getChildren().isEmpty()) {
            if (profileCard.getChildren().get(0) instanceof StackPane avatar &&
                    !avatar.getChildren().isEmpty() &&
                    avatar.getChildren().get(0) instanceof Label initials) {

                String base = (name != null && !name.isBlank()) ? name : email;
                initials.setText(getInitials(base));
            }
        }
    }
}
