package pt.isec.client.ui.util;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.stage.Window;
import java.util.Optional;

public final class AlertUtils {

    private AlertUtils() { }

    public static void showInfo(Window owner, String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        if (owner != null) {
            alert.initOwner(owner);
        }
        alert.setTitle(title != null ? title : "Informação");
        alert.setHeaderText(null);
        alert.setContentText(message != null ? message : "");
        alert.showAndWait();
    }

    public static void showError(Window owner, String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        if (owner != null) {
            alert.initOwner(owner);
        }
        alert.setTitle(title != null ? title : "Erro");
        alert.setHeaderText(null);
        alert.setContentText(message != null ? message : "");
        alert.showAndWait();
    }

    /**
     * Mostra um diálogo de confirmação e devolve true se o utilizador carregou em OK.
     */
    public static boolean showConfirmation(Window owner,
                                           String title,
                                           String header,
                                           String message) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        if (owner != null) {
            alert.initOwner(owner);
        }
        alert.setTitle(title != null ? title : "Confirmar");
        alert.setHeaderText(header);
        alert.setContentText(message != null ? message : "");

        Optional<ButtonType> result = alert.showAndWait();
        return result.isPresent() && result.get() == ButtonType.OK;
    }
}
