package pt.isec.client.ui.teacher;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

/**
 * Teacher dashboard view (dark theme).
 * <p>
 * Layout and styling are defined in {@code dashboard.css}.
 */
public class TeacherDashboardView {

    private String userEmail;
    private String userName;

    private Label profileNameLabel;
    private Label avatarInitialsLabel;
    private VBox profileCard;

    private Label totalQuestionsValueLabel;
    private Label activeQuestionsValueLabel;
    private Label answersReceivedValueLabel;

    private Scene scene;
    private Label welcomeLabel;
    private Label emailLabel;
    private Label headerEmailLabel;
    private TextArea notificationArea;
    private VBox mainContentArea;

    // Buttons
    private Button createQuestionBtn;
    private Button listQuestionsBtn; // kept for compatibility
    private Button logoutBtn;
    private Button manageQuestionsBtn;
    private Button profileBtn;

    /**
     * Creates a new teacher dashboard view.
     *
     * @param userName teacher name
     * @param userEmail teacher email
     */
    public TeacherDashboardView(String userName, String userEmail) {
        this.userName = userName;
        this.userEmail = userEmail;
    }

    /**
     * Builds the scene and all UI components.
     */
    public void createView() {
        BorderPane root = new BorderPane();
        root.getStyleClass().add("dashboard-root-dark");

        root.setTop(createHeader());
        root.setLeft(createSidebar());

        mainContentArea = new VBox(20);
        mainContentArea.getStyleClass().add("dashboard-main-area");
        mainContentArea.setPadding(new Insets(20));
        showWelcomeView();
        root.setCenter(mainContentArea);

        scene = new Scene(root, 1100, 750);
        try {
            var cssUrl = getClass().getResource("/styles/dashboard.css");
            if (cssUrl != null) {
                scene.getStylesheets().add(cssUrl.toExternalForm());
            }
        } catch (Exception ignored) {
        }
    }

    private VBox createHeader() {
        VBox header = new VBox(10);
        header.getStyleClass().add("dashboard-header-dark");

        welcomeLabel = new Label("Bem-vindo, " +
                (userName != null ? userName : "Docente") + "!");
        welcomeLabel.setFont(Font.font("Arial", FontWeight.NORMAL, 24));
        welcomeLabel.getStyleClass().add("header-welcome");

        emailLabel = new Label(userEmail != null ? userEmail : "");
        emailLabel.getStyleClass().add("header-email-dark");

        header.getChildren().addAll(welcomeLabel, emailLabel);
        return header;
    }

    private VBox createProfileCard() {
        VBox box = new VBox(8);
        box.getStyleClass().add("profile-card");
        box.setAlignment(Pos.CENTER);

        StackPane avatar = new StackPane();
        avatar.getStyleClass().add("profile-avatar-circle");

        Label initialsLabel = new Label(getInitials(userName != null ? userName : userEmail));
        initialsLabel.getStyleClass().add("profile-avatar-initials");
        avatar.getChildren().add(initialsLabel);

        profileNameLabel = new Label(userName != null ? userName : "Docente");
        profileNameLabel.getStyleClass().add("profile-name-label");

        Label roleLabel = new Label("Docente");
        roleLabel.getStyleClass().add("profile-role-label");

        box.getChildren().addAll(avatar, profileNameLabel, roleLabel);
        return box;
    }

    private VBox createSidebar() {
        VBox sidebar = new VBox(12);
        sidebar.getStyleClass().add("dashboard-sidebar-dark");
        sidebar.setPrefWidth(240);

        profileCard = createProfileCard();

        Label menuLabel = new Label("MENU");
        menuLabel.getStyleClass().add("sidebar-title-dark");

        profileBtn = createMenuButton("Perfil");
        createQuestionBtn = createMenuButton("Criar Pergunta");
        manageQuestionsBtn = createMenuButton("Gerir Perguntas");
        logoutBtn = createMenuButton("Logout");

        sidebar.getChildren().addAll(
                profileCard,
                new Separator(),
                menuLabel,
                new Separator(),
                profileBtn,
                createQuestionBtn,
                manageQuestionsBtn,
                logoutBtn
        );

        return sidebar;
    }

    private Button createMenuButton(String text) {
        Button btn = new Button(text);
        btn.getStyleClass().add("sidebar-button-dark");
        btn.setPrefWidth(Double.MAX_VALUE);
        return btn;
    }

    private String getInitials(String text) {
        if (text == null || text.isBlank()) {
            return "?";
        }
        String[] parts = text.trim().split("\\s+");
        if (parts.length == 1) {
            return parts[0].substring(0, 1).toUpperCase();
        }
        return (parts[0].substring(0, 1) + parts[parts.length - 1].substring(0, 1)).toUpperCase();
    }

    @SuppressWarnings("unused")
    private String deriveNameFromEmail(String email) {
        if (email == null || !email.contains("@")) {
            return "Docente";
        }
        String part = email.substring(0, email.indexOf('@'));
        if (part.isEmpty()) {
            return "Docente";
        }
        return Character.toUpperCase(part.charAt(0)) + part.substring(1);
    }

    /**
     * Updates the profile name in card and header.
     *
     * @param name new display name
     */
    public void setProfileName(String name) {
        if (name == null || name.isBlank()) {
            return;
        }
        this.userName = name;
        if (profileNameLabel != null) {
            profileNameLabel.setText(name);
        }
        if (welcomeLabel != null) {
            welcomeLabel.setText("Bem-vindo, " + name + "!");
        }
        if (avatarInitialsLabel != null) {
            avatarInitialsLabel.setText(getInitials(name));
        }
    }

    /**
     * Returns the current profile name displayed in the view.
     *
     * @return profile name
     */
    public String getProfileName() {
        return profileNameLabel != null ? profileNameLabel.getText() : userName;
    }

    /**
     * Shows the default welcome view with summary cards and notifications.
     */
    public void showWelcomeView() {
        mainContentArea.getChildren().clear();
        mainContentArea.setAlignment(Pos.TOP_CENTER);
        mainContentArea.setSpacing(25);

        Label titleLabel = new Label("Painel do Docente");
        titleLabel.setFont(Font.font("Arial", FontWeight.NORMAL, 28));
        titleLabel.getStyleClass().add("dashboard-title");

        Label notifLabel = new Label("Notificações");
        notifLabel.setFont(Font.font("Arial", FontWeight.NORMAL, 18));
        notifLabel.getStyleClass().add("dashboard-section-title");

        notificationArea = new TextArea();
        notificationArea.setEditable(false);
        notificationArea.setPrefHeight(150);
        notificationArea.setPromptText("Sem notificações");
        notificationArea.getStyleClass().add("notification-area-dark");
        notificationArea.setWrapText(true);

        HBox cards = new HBox(30);
        cards.setAlignment(Pos.CENTER);

        VBox totalCard = new VBox(8);
        totalCard.getStyleClass().add("info-card-dark");
        totalQuestionsValueLabel = new Label("0");
        totalQuestionsValueLabel.getStyleClass().add("info-card-value-dark");
        Label totalLabel = new Label("Total de Perguntas");
        totalLabel.getStyleClass().add("info-card-title-dark");
        totalCard.getChildren().addAll(totalQuestionsValueLabel, totalLabel);

        VBox activeCard = new VBox(8);
        activeCard.getStyleClass().add("info-card-dark");
        activeQuestionsValueLabel = new Label("0");
        activeQuestionsValueLabel.getStyleClass().add("info-card-value-dark");
        Label activeLabel = new Label("Perguntas Ativas");
        activeLabel.getStyleClass().add("info-card-title-dark");
        activeCard.getChildren().addAll(activeQuestionsValueLabel, activeLabel);

        VBox answersCard = new VBox(8);
        answersCard.getStyleClass().add("info-card-dark");
        answersReceivedValueLabel = new Label("0");
        answersReceivedValueLabel.getStyleClass().add("info-card-value-dark");
        Label answersLabel = new Label("Respostas Recebidas");
        answersLabel.getStyleClass().add("info-card-title-dark");
        answersCard.getChildren().addAll(answersReceivedValueLabel, answersLabel);

        cards.getChildren().addAll(totalCard, activeCard, answersCard);

        mainContentArea.getChildren().addAll(
                titleLabel,
                new Separator(),
                notifLabel,
                notificationArea,
                new Label(),
                cards
        );
    }

    /**
     * Updates the numeric statistics shown in the info cards.
     *
     * @param totalQuestions   total number of questions
     * @param activeQuestions  number of active questions
     * @param totalAnswers     total number of answers received
     */
    public void updateStats(int totalQuestions, int activeQuestions, int totalAnswers) {
        if (totalQuestionsValueLabel != null) {
            totalQuestionsValueLabel.setText(String.valueOf(totalQuestions));
        }
        if (activeQuestionsValueLabel != null) {
            activeQuestionsValueLabel.setText(String.valueOf(activeQuestions));
        }
        if (answersReceivedValueLabel != null) {
            answersReceivedValueLabel.setText(String.valueOf(totalAnswers));
        }
    }

    /**
     * Updates the welcome message with the given name.
     *
     * @param name teacher name
     */
    public void setWelcomeName(String name) {
        if (welcomeLabel == null) {
            return;
        }
        if (name == null || name.trim().isEmpty()) {
            welcomeLabel.setText("Bem-vindo, Docente!");
        } else {
            welcomeLabel.setText("Bem-vindo, " + name + "!");
        }
    }

    /**
     * Gets the JavaFX scene for this view.
     *
     * @return scene
     */
    public Scene getScene() {
        return scene;
    }

    /**
     * Registers event handlers that delegate to the given controller.
     *
     * @param controller teacher dashboard controller
     */
    public void registerHandlers(TeacherDashboardController controller) {
        profileBtn.setOnAction(e -> controller.onEditProfile());
        createQuestionBtn.setOnAction(e -> controller.onCreateQuestion());
        manageQuestionsBtn.setOnAction(e -> controller.onManageQuestions());
        logoutBtn.setOnAction(e -> controller.onLogout());

        if (profileCard != null) {
            profileCard.setOnMouseClicked(e -> controller.onEditProfile());
        }
    }

    /**
     * Hook for future UI updates.
     */
    public void update() {
        // nothing extra for now
    }

    /**
     * Appends a timestamped notification message to the notification area.
     *
     * @param message notification text
     */
    public void addNotification(String message) {
        if (notificationArea == null) {
            return;
        }
        String timestamp = java.time.LocalTime.now().format(
                java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss")
        );
        notificationArea.appendText("[" + timestamp + "] " + message + "\n");
    }

    /**
     * Updates name/email across the dashboard (header + profile card + avatar).
     *
     * @param name  new name
     * @param email new email
     */
    public void updateUserInfo(String name, String email) {
        if (name != null && !name.isBlank()) {
            this.userName = name;
            if (profileNameLabel != null) {
                profileNameLabel.setText(name);
            }
            if (welcomeLabel != null) {
                welcomeLabel.setText("Bem-vindo, " + name + "!");
            }
        }

        if (email != null && !email.isBlank()) {
            this.userEmail = email;
            if (emailLabel != null) {
                emailLabel.setText(email);
            }
        }
    }
}
