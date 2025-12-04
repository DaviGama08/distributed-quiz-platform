package pt.isec.client.ui.util;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.stage.Window;

/**
 * Utility class to handle common modal dialogs (information, error, confirmation).
 */
public final class AlertUtils {

    private AlertUtils() {}

    /**
     * Shows an information dialog.
     *
     * @param owner   owner window (may be {@code null})
     * @param title   dialog title (defaults to "Informação" if {@code null})
     * @param message dialog message (empty if {@code null})
     */
    public static void showInfo(Window owner, String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        if (owner != null) {
            alert.initOwner(owner);
        }

        alert.setTitle(title != null && !title.isBlank() ? title : "Informação");
        alert.setHeaderText(null);
        alert.setContentText(message != null ? message : "");

        alert.showAndWait();
    }

    /**
     * Shows an error dialog.
     *
     * @param owner   owner window (may be {@code null})
     * @param message dialog message (empty if {@code null})
     */
    public static void showError(Window owner, String header, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        if (owner != null) {
            alert.initOwner(owner);
        }

        alert.setTitle("Erro");
        alert.setHeaderText(
                header != null && !header.isBlank() ? header : null
        );
        alert.setContentText(
                message != null && !message.isBlank() ? message : ""
        );

        alert.showAndWait();
    }

    /**
     * Shows a confirmation dialog and returns {@code true} if the user pressed OK.
     *
     * @param owner  owner window (may be {@code null})
     * @param title  dialog title (defaults to "Confirmar" if {@code null})
     * @param header dialog header text (may be {@code null})
     * @return {@code true} if user pressed OK, {@code false} otherwise
     */
    public static boolean showConfirmation(Window owner,
                                           String title,
                                           String header,
                                           String content) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        if (owner != null) {
            alert.initOwner(owner);
        }

        alert.setTitle(title != null && !title.isBlank() ? title : "Confirmar");
        alert.setHeaderText(header);
        alert.setContentText(content != null ? content : "");

        return alert.showAndWait()
                .filter(btn -> btn == ButtonType.OK || btn == ButtonType.YES)
                .isPresent();
    }
}
