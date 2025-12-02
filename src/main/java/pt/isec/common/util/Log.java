package pt.isec.common.util;

import java.io.PrintStream;

/**
 * Simple logger to standardize console messages.
 * <p>
 * Format:
 * <pre>
 *   [LEVEL][ClassName] message
 * </pre>
 *
 * LEVEL: INFO, WARN, ERROR
 */
public final class Log {

    private Log() {
        // static utility
    }

    /* ===================== INFO methods ===================== */

    /**
     * Logs an informational message.
     *
     * @param source  source class
     * @param message message text
     */
    public static void info(Class<?> source, String message) {
        log("INFO", source, message, null, true);
    }

    /**
     * Logs a formatted informational message.
     *
     * @param source source class
     * @param format message format
     * @param args   format arguments
     */
    public static void info(Class<?> source, String format, Object... args) {
        String msg = String.format(format, args);
        String nl = System.lineSeparator();
        boolean endsWithNl = msg.endsWith(nl);
        if (endsWithNl) {
            msg = msg.substring(0, msg.length() - nl.length());
        }
        log("INFO", source, msg, null, true);
    }

    /* ===================== WARN methods ===================== */

    /**
     * Logs a warning message.
     *
     * @param source  source class
     * @param message message text
     */
    public static void warn(Class<?> source, String message) {
        log("WARN", source, message, null, true);
    }

    /**
     * Logs a formatted warning message.
     *
     * @param source source class
     * @param format message format
     * @param args   format arguments
     */
    public static void warn(Class<?> source, String format, Object... args) {
        String msg = String.format(format, args);
        String nl = System.lineSeparator();
        boolean endsWithNl = msg.endsWith(nl);
        if (endsWithNl) {
            msg = msg.substring(0, msg.length() - nl.length());
        }
        log("WARN", source, msg, null, true);
    }

    /* ===================== ERROR methods ===================== */

    /**
     * Logs an error message.
     *
     * @param source  source class
     * @param message message text
     */
    public static void error(Class<?> source, String message) {
        log("ERROR", source, message, null, true);
    }

    /**
     * Logs a formatted error message.
     *
     * @param source source class
     * @param format message format
     * @param args   format arguments
     */
    public static void error(Class<?> source, String format, Object... args) {
        String msg = String.format(format, args);
        String nl = System.lineSeparator();
        boolean endsWithNl = msg.endsWith(nl);
        if (endsWithNl) {
            msg = msg.substring(0, msg.length() - nl.length());
        }
        log("ERROR", source, msg, null, true);
    }

    /**
     * Logs an error message with an associated {@link Throwable}.
     *
     * @param source  source class
     * @param message message text
     * @param t       throwable to log
     */
    public static void error(Class<?> source, String message, Throwable t) {
        log("ERROR", source, message, t, true);
    }

    /* ===================== Core ===================== */

    /**
     * Core logging implementation.
     *
     * @param level   log level string
     * @param source  source class
     * @param message message text
     * @param t       optional throwable
     * @param newline whether to append newline
     */
    private static void log(String level, Class<?> source, String message, Throwable t, boolean newline) {
        String className = (source != null ? source.getSimpleName() : "UNKNOWN");
        String prefix = "[" + level + "][" + className + "] ";
        PrintStream ps = "ERROR".equals(level) ? System.err : System.out;

        if (newline) {
            ps.println(prefix + message);
        } else {
            ps.print(prefix + message);
        }

        if (t != null) {
            t.printStackTrace(ps);
        }
    }
}
