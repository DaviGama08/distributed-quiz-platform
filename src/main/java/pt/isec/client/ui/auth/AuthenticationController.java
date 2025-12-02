package pt.isec.client.ui.auth;

import javafx.scene.paint.Color;
import javafx.stage.Stage;
import pt.isec.client.ClientApplication;
import pt.isec.client.ClientManager;
import pt.isec.client.core.ClientService;
import pt.isec.client.ui.IDisposableProp;
import pt.isec.client.ui.util.UiUtils;
import pt.isec.common.dto.auth.AuthResponseDTO;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

/**
 * Controller for the authentication interface.
 * <p>
 * Responsible for:
 * <ul>
 *     <li>Validating input</li>
 *     <li>Calling authentication services</li>
 *     <li>Reacting to login/register responses</li>
 *     <li>Navigating to the appropriate dashboard</li>
 * </ul>
 */
public class AuthenticationController implements IDisposableProp {

    private enum Mode { LOGIN, REGISTER }

    private static final Color BLUE = Color.web("#3498db");
    private static final Color RED = Color.web("#A01316");

    private final Stage stage;
    private final ClientManager clientManager;
    private final ClientApplication application;
    private final AuthenticationView view;

    private Mode mode = Mode.LOGIN;
    private volatile boolean authBusy = false;

    // Listeners stored as fields for proper removal
    private final PropertyChangeListener authListener = this::handleAuthenticationChange;
    private final PropertyChangeListener loginOkListener = this::handleLoginSuccessResponse;
    private final PropertyChangeListener loginFailListener = this::handleLoginFailResponse;
    private final PropertyChangeListener registerOkListener = this::handleRegisterOkResponse;
    private final PropertyChangeListener connectionStatusListener = this::handleConnectionStatusChange;

    /**
     * Creates a new authentication controller.
     *
     * @param stage         primary stage
     * @param clientManager client manager for service access
     * @param application   main JavaFX application
     */
    public AuthenticationController(Stage stage, ClientManager clientManager, ClientApplication application) {
        this.stage = stage;
        this.clientManager = clientManager;
        this.application = application;
        this.view = new AuthenticationView();
        this.view.createView();
        this.view.registerHandlers(this);
        view.showLoginMode();
        setupPropertyChangeListeners();
    }

    /**
     * Registers all needed property change listeners on {@link ClientService}.
     */
    private void setupPropertyChangeListeners() {
        ClientService service = clientManager.getService();

        service.addPropertyChangeListener(ClientService.PROP_AUTHENTICATED, authListener);
        service.addPropertyChangeListener(ClientService.PROP_LOGIN_OK, loginOkListener);
        service.addPropertyChangeListener(ClientService.PROP_LOGIN_FAIL, loginFailListener);
        service.addPropertyChangeListener(ClientService.PROP_REGISTER_OK, registerOkListener);
        service.addPropertyChangeListener(ClientService.PROP_CONNECTION_STATUS, connectionStatusListener);
    }

    /**
     * Handles authentication state changes (authenticated / not authenticated).
     */
    private void handleAuthenticationChange(PropertyChangeEvent evt) {
        boolean authenticated = (boolean) evt.getNewValue();
        if (authenticated) {
            UiUtils.runOnUiThread(this::openDashboard);
        }
    }

    /**
     * Opens the proper dashboard (teacher / student) according to the user type.
     */
    private void openDashboard() {
        view.clearLoginFields();
        view.clearRegisterFields();

        ClientService service = clientManager.getService();
        String userType = service.getUserType();
        String email = service.getUserEmail();
        String name = service.getUserName();

        if ("TEACHER".equalsIgnoreCase(userType)) {
            application.showTeacherDashboard(name, email);
        } else {
            application.showStudentDashboard(name, email);
        }
    }

    /**
     * Handles successful login responses.
     */
    private void handleLoginSuccessResponse(PropertyChangeEvent evt) {
        AuthResponseDTO data = (AuthResponseDTO) evt.getNewValue();
        ClientService service = clientManager.getService();
        try {
            service.setUserId(Integer.parseInt(data.userId()));
        } catch (NumberFormatException ignored) {
            service.setUserId(null);
        }
        service.setUserType(data.userType());
        service.setUserEmail(data.email());
        service.setUserName(data.name());
        service.setAuthenticated(true);

        UiUtils.runOnUiThread(() ->
                view.setLoginStatus("✔️ Login OK! A carregar dashboard...", Color.GREEN, false, true)
        );
    }

    /**
     * Handles both {@code LOGIN_FAIL} and generic {@code ERROR} responses.
     * <p>
     * If we are in LOGIN mode, shows message in login form;
     * if we are in REGISTER mode, shows message in register form.
     */
    private void handleLoginFailResponse(PropertyChangeEvent evt) {
        String data = (String) evt.getNewValue();
        UiUtils.runOnUiThread(() -> {
            if (mode == Mode.LOGIN) {
                view.setLoginStatus("❌ Login Falhou: " + data, RED, false, true);
            } else {
                view.setRegisterStatus("❌ Registo Falhou: " + data, RED, true);
            }
            authBusy = false;
            view.setAuthBusy(false);
        });
    }

    /**
     * Handles successful register responses.
     */
    private void handleRegisterOkResponse(PropertyChangeEvent evt) {
        AuthResponseDTO data = (AuthResponseDTO) evt.getNewValue();
        ClientService service = clientManager.getService();

        try {
            service.setUserId(Integer.parseInt(data.userId()));
        } catch (NumberFormatException ignored) {
            service.setUserId(null);
        }
        service.setUserType(data.userType());
        service.setUserEmail(data.email());
        service.setUserName(data.name());

        UiUtils.runOnUiThread(() -> {
            view.setRegisterStatus(
                    "✔️ Registo concluído! Já pode iniciar sessão com os seus dados.",
                    Color.GREEN,
                    true
            );

            view.clearRegisterFields();
            view.clearLoginFields();

            if (data != null && data.email() != null) {
                view.prefillLoginEmail(data.email());
            }

            mode = Mode.LOGIN;
            view.showLoginMode();

            authBusy = false;
            view.setAuthBusy(false);
        });
    }

    /**
     * Handles connection status changes (directory/server).
     */
    private void handleConnectionStatusChange(PropertyChangeEvent evt) {
        String status = (String) evt.getNewValue();

        switch (status) {
            case "DIRECTORY_CONNECTING" -> UiUtils.runOnUiThread(() ->
                    view.showGlobalLoading("A contactar a diretoria...")
            );
            case "SERVER_CONNECTING" -> UiUtils.runOnUiThread(() ->
                    view.showGlobalLoading("A ligar ao servidor principal...")
            );
            case "CONNECTED" -> UiUtils.runOnUiThread(() -> {
                view.hideGlobalLoading();
                if (!authBusy) {
                    view.setLoginStatus(
                            "Ligação estabelecida. Introduza as suas credenciais.",
                            BLUE,
                            false,
                            true
                    );
                }
            });
            case "DISCONNECTED" -> UiUtils.runOnUiThread(() ->
                    view.showGlobalLoading("Ligação ao servidor perdida. A tentar reconectar...")
            );
            case "DIRECTORY_ERROR" -> {
                UiUtils.runOnUiThread(() ->
                        view.showGlobalError(
                                "Não foi possível contactar a diretoria/servidor. \nA aplicação vai encerrar..."
                        )
                );
                try {
                    Thread.sleep(3000);
                } catch (InterruptedException ignored) {
                }
            }
            case "SERVER_ERROR" -> UiUtils.runOnUiThread(() ->
                    view.showGlobalError(
                            "Não foi possível ligar ao servidor principal.\nA aplicação vai encerrar..."
                    )
            );
            default -> {
            }
        }
    }

    /**
     * Toggles between login and register modes.
     */
    public void onToggleMode() {
        if (authBusy) {
            return;
        }
        if (mode == Mode.LOGIN) {
            mode = Mode.REGISTER;
            view.showRegisterMode();
        } else {
            mode = Mode.LOGIN;
            view.showLoginMode();
        }
    }

    /**
     * Login button handler.
     */
    public void onLogin() throws InterruptedException {
        if (mode != Mode.LOGIN || authBusy) {
            return;
        }

        String email = view.getLoginEmail();
        String password = view.getLoginPassword();

        if (email.isEmpty() || password.isEmpty()) {
            showLoginError("Por favor, preencha todos os campos.");
            return;
        }
        if (!email.contains("@")) {
            showLoginError("Email inválido.");
            return;
        }

        setAuthBusy(true);
        view.setLoginStatus("A autenticar...", BLUE, true, false);

        clientManager.getAuthService().login(email, password);
    }

    /**
     * Register button handler.
     */
    public void onRegister() {
        if (mode != Mode.REGISTER || authBusy) {
            return;
        }

        String type = view.getSelectedRegisterType();
        String name = view.getRegisterName();
        String email = view.getRegisterEmail();
        String password = view.getRegisterPassword();
        String extra = view.getRegisterExtra();

        if (name.isEmpty() || email.isEmpty() || password.isEmpty() || extra.isEmpty()) {
            showRegisterError("Por favor, preencha todos os campos.");
            return;
        }
        if (!email.contains("@")) {
            showRegisterError("Email inválido.");
            return;
        }
        if (password.length() < 6) {
            showRegisterError("Password deve ter no mínimo 6 caracteres.");
            return;
        }

        setAuthBusy(true);
        view.setRegisterStatus("A registar...", BLUE, false);

        try {
            if ("STUDENT".equalsIgnoreCase(type)) {
                int number;
                try {
                    number = Integer.parseInt(extra);
                } catch (NumberFormatException e) {
                    UiUtils.runOnUiThread(() ->
                            showRegisterError("Número de estudante inválido.")
                    );
                    return;
                }
                clientManager.getAuthService().registerStudent(name, email, password, number);
            } else {
                clientManager.getAuthService().registerTeacher(name, email, password, extra);
            }
        } catch (Exception e) {
            UiUtils.runOnUiThread(() ->
                    showRegisterError("Erro no registo: " + e.getMessage())
            );
            setAuthBusy(false);
        }
    }

    /**
     * Handler for changes in register type (student/teacher).
     *
     * @param type "STUDENT" or "TEACHER"
     */
    public void onRegisterTypeChanged(String type) {
        if ("STUDENT".equalsIgnoreCase(type)) {
            view.setRegisterExtraLabel("Número de Estudante");
        } else {
            view.setRegisterExtraLabel("Código de Docente");
        }
    }

    /**
     * Sets the busy state and updates the view.
     */
    private void setAuthBusy(boolean busy) {
        authBusy = busy;
        UiUtils.runOnUiThread(() -> view.setAuthBusy(busy));
    }

    private void showLoginError(String message) {
        view.setLoginStatus("❌ " + message, RED, false, true);
    }

    private void showRegisterError(String message) {
        view.setRegisterStatus("❌ " + message, RED, true);
    }

    /**
     * Resets the authentication view and shows it.
     */
    public void show() {
        authBusy = false;
        setAuthBusy(false);
        mode = Mode.LOGIN;
        view.showLoginMode();
        view.setLoginStatus("", BLUE, false, true);
        view.setRegisterStatus("", BLUE, true);
        view.clearLoginFields();
        view.clearRegisterFields();
        view.showBusy(false);
        stage.setScene(view.getScene());
    }

    /**
     * Removes all listeners registered on the {@link ClientService}.
     */
    @Override
    public void dispose() {
        ClientService service = clientManager.getService();
        if (service == null) {
            return;
        }

        service.removePropertyChangeListener(ClientService.PROP_AUTHENTICATED, authListener);
        service.removePropertyChangeListener(ClientService.PROP_LOGIN_OK, loginOkListener);
        service.removePropertyChangeListener(ClientService.PROP_LOGIN_FAIL, loginFailListener);
        service.removePropertyChangeListener(ClientService.PROP_REGISTER_OK, registerOkListener);
        service.removePropertyChangeListener(ClientService.PROP_CONNECTION_STATUS, connectionStatusListener);
    }
}
