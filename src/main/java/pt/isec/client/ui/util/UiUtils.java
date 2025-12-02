package pt.isec.client.ui.util;

import javafx.application.Platform;

/**
 * Utility helper methods for JavaFX UI-related operations.
 */
public final class UiUtils {

    private UiUtils() {
        // utility class
    }

    /**
     * Ensures that the given runnable is executed on the JavaFX Application Thread.
     * <p>
     * If the current thread is already the JavaFX thread, the runnable is executed
     * immediately; otherwise it is scheduled via {@link Platform#runLater(Runnable)}.
     *
     * @param runnable action to execute on the UI thread (may be {@code null})
     */
    public static void runOnUiThread(Runnable runnable) {
        if (runnable == null) {
            return;
        }

        if (Platform.isFxApplicationThread()) {
            runnable.run();
        } else {
            Platform.runLater(runnable);
        }
    }

    /**
     * Builds initials from a given text or name.
     * <p>
     * Examples:
     * <ul>
     *     <li>{@code "João Silva" -> "JS"}</li>
     *     <li>{@code "Maria" -> "M"}</li>
     *     <li>{@code null / empty -> "?"}</li>
     * </ul>
     *
     * @param text full name or arbitrary text
     * @return initials, or {@code "?"} if the text is {@code null} or blank
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
