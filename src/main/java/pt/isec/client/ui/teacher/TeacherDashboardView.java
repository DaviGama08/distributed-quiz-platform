package pt.isec.client.ui.teacher;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

/**
 * Dashboard do docente (tema escuro com acentos vermelhos).
 */
public class TeacherDashboardView {
    private final String userEmail;
    private String userName;

    private String displayName;
    private Label profileNameLabel;
    private Button overviewBtn;
    private VBox profileCard;

    private Label totalQuestionsValueLabel;
    private Label activeQuestionsValueLabel;
    private Label answersReceivedValueLabel;

    private Scene scene;
    private Label welcomeLabel;
    private TextArea notificationArea;
    private VBox mainContentArea;

    // botões
    private Button createQuestionBtn;
    private Button listQuestionsBtn;
    private Button logoutBtn;

    public TeacherDashboardView(String userName, String userEmail) {
        this.userName = userName;
        this.userEmail = userEmail;
    }

    public void createView() {
        BorderPane root = new BorderPane();
        root.getStyleClass().add("dashboard-root-dark");

        // Cabeçalho
        root.setTop(createHeader());

        // Sidebar
        root.setLeft(createSidebar());

        // Área principal
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
        } catch (Exception ignored) { }
    }

    private VBox createHeader() {
        VBox header = new VBox(10);
        header.getStyleClass().add("dashboard-header-dark");

        welcomeLabel = new Label("Bem-vindo, " +
                (userName != null ? userName : "Docente") + "!");
        welcomeLabel.setFont(Font.font("Arial", FontWeight.NORMAL, 24));        welcomeLabel.setFont(Font.font("Arial", FontWeight.NORMAL, 24));
        welcomeLabel.getStyleClass().add("header-welcome");

        Label emailLabel = new Label(userEmail);
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

        Label initialsLabel = new Label(getInitials(userName));
        initialsLabel.getStyleClass().add("profile-avatar-initials");
        avatar.getChildren().add(initialsLabel);

        profileNameLabel = new Label(userName);
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

        // Título do menu
        Label menuLabel = new Label("MENU");
        menuLabel.getStyleClass().add("sidebar-title-dark");

        // Cartão de perfil (clicável)
        profileCard = createProfileCard();

        // Botões do menu
        createQuestionBtn = createMenuButton("Criar Pergunta");
        listQuestionsBtn = createMenuButton("Listar Perguntas");
        logoutBtn = createMenuButton("Logout");

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);

        sidebar.getChildren().addAll(
                profileCard,
                new Separator(),
                menuLabel,
                new Separator(),
                createQuestionBtn,
                listQuestionsBtn,
                spacer,
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

    private String deriveNameFromEmail(String email) {
        if (email == null || !email.contains("@"))
            return "Docente";
        String part = email.substring(0, email.indexOf('@'));
        if (part.isEmpty()) return "Docente";
        return Character.toUpperCase(part.charAt(0)) + part.substring(1);
    }

    private String getInitials(String name) {
        if (name == null || name.isBlank())
            return "?";
        String[] parts = name.trim().split("\\s+");
        if (parts.length == 1)
            return parts[0].substring(0, 1).toUpperCase();
        return (parts[0].substring(0, 1) + parts[parts.length - 1].substring(0, 1)).toUpperCase();
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
        // Card Total de Perguntas
        VBox totalCard = new VBox(8);
        totalCard.getStyleClass().add("info-card-dark");
        totalQuestionsValueLabel = new Label("0");
        totalQuestionsValueLabel.getStyleClass().add("info-card-value-dark");
        Label totalLabel = new Label("Total de Perguntas");
        totalLabel.getStyleClass().add("info-card-title-dark");
        totalCard.getChildren().addAll(totalQuestionsValueLabel, totalLabel);

        // Card Perguntas Ativas
        VBox activeCard = new VBox(8);
        activeCard.getStyleClass().add("info-card-dark");
        activeQuestionsValueLabel = new Label("0");
        activeQuestionsValueLabel.getStyleClass().add("info-card-value-dark");
        Label activeLabel = new Label("Perguntas Ativas");
        activeLabel.getStyleClass().add("info-card-title-dark");
        activeCard.getChildren().addAll(activeQuestionsValueLabel, activeLabel);

        // Card Respostas Recebidas
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

    private VBox createInfoCard(String title, String value) {
        VBox card = new VBox(8);
        card.getStyleClass().add("info-card-dark");

        Label valueLabel = new Label(value);
        valueLabel.getStyleClass().add("info-card-value-dark");

        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("info-card-title-dark");

        card.getChildren().addAll(valueLabel, titleLabel);
        return card;
    }

    public void updateStats(int totalQuestions, int activeQuestions, int totalAnswers) {
        if (totalQuestionsValueLabel != null)
            totalQuestionsValueLabel.setText(String.valueOf(totalQuestions));
        if (activeQuestionsValueLabel != null)
            activeQuestionsValueLabel.setText(String.valueOf(activeQuestions));
        if (answersReceivedValueLabel != null)
            answersReceivedValueLabel.setText(String.valueOf(totalAnswers));
    }

    /** NOVO: actualiza o texto de boas-vindas com o nome do docente */
    public void setWelcomeName(String name) {
        if (name == null || name.trim().isEmpty()) {
            welcomeLabel.setText("Bem-vindo, Docente!");
        } else {
            welcomeLabel.setText("Bem-vindo, " + name + "!");
        }
    }

    public Scene getScene() { return scene; }

    public void registerHandlers(TeacherDashboardController controller) {
        createQuestionBtn.setOnAction(e -> controller.onCreateQuestion());
        listQuestionsBtn.setOnAction(e -> controller.onListQuestions());
        logoutBtn.setOnAction(e -> controller.onLogout());

        // clicar no cartão de perfil abre o modal de edição de perfil
        if (profileCard != null) {
            profileCard.setOnMouseClicked(e -> controller.onEditProfile());
        }
    }

    public void update() {
        // não requerido por enquanto
    }

    public void addNotification(String message) {
        if (notificationArea == null) return;
        String timestamp = java.time.LocalTime.now().format(
                java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss")
        );
        notificationArea.appendText("[" + timestamp + "] " + message + "\n");
    }
}
