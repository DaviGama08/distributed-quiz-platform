package pt.isec.client.ui.util;

import javafx.application.Platform;

public final class UiUtils {

    private UiUtils() { }

    /**
     * Garante que o código corre na JavaFX Application Thread.
     */
    public static void runOnUiThread(Runnable runnable) {
        if (runnable == null)
            return;

        if (Platform.isFxApplicationThread()) {
            runnable.run();
        } else {
            Platform.runLater(runnable);
        }
    }

    /**
     * Devolve as iniciais a partir de um nome ou texto.
     * Exemplos:
     *  - "João Silva" -> "JS"
     *  - "Maria"      -> "M"
     *  - null / vazio -> "?"
     */
    public static String getInitials(String text) {
        if (text == null || text.isBlank()) {
            return "?";
        }

        String[] parts = text.trim().split("\\s+");
        if (parts.length == 1) {
            return parts[0].substring(0, 1).toUpperCase();
        }

        char first = parts[0].charAt(0);
        char last = parts[parts.length - 1].charAt(0);
        return ("" + first + last).toUpperCase();
    }
}
