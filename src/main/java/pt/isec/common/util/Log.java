package pt.isec.common.util;

import java.io.PrintStream;

/**
 * Logger simples para uniformizar as mensagens na consola.
 *
 * Formato:
 *   [LEVEL][ClassName] mensagem
 *
 * LEVEL: INFO, WARN, ERROR
 */
public final class Log {

    private Log() {
        // utilitário estático
    }

    /* ===================== Métodos INFO ===================== */

    public static void info(Class<?> source, String message) {
        log("INFO", source, message, null, true);
    }

    public static void info(Class<?> source, String format, Object... args) {
        String msg = String.format(format, args);
        // Se vier de um printf("...%n"), retiramos a quebra de linha e deixamos o println tratar disso
        String nl = System.lineSeparator();
        boolean endsWithNl = msg.endsWith(nl);
        if (endsWithNl) {
            msg = msg.substring(0, msg.length() - nl.length());
        }
        log("INFO", source, msg, null, true);
    }

    /* ===================== Métodos WARN ===================== */

    public static void warn(Class<?> source, String message) {
        log("WARN", source, message, null, true);
    }

    public static void warn(Class<?> source, String format, Object... args) {
        String msg = String.format(format, args);
        String nl = System.lineSeparator();
        boolean endsWithNl = msg.endsWith(nl);
        if (endsWithNl) {
            msg = msg.substring(0, msg.length() - nl.length());
        }
        log("WARN", source, msg, null, true);
    }

    /* ===================== Métodos ERROR ===================== */

    public static void error(Class<?> source, String message) {
        log("ERROR", source, message, null, true);
    }

    public static void error(Class<?> source, String format, Object... args) {
        String msg = String.format(format, args);
        String nl = System.lineSeparator();
        boolean endsWithNl = msg.endsWith(nl);
        if (endsWithNl) {
            msg = msg.substring(0, msg.length() - nl.length());
        }
        log("ERROR", source, msg, null, true);
    }

    public static void error(Class<?> source, String message, Throwable t) {
        log("ERROR", source, message, t, true);
    }

    /* ===================== Core ===================== */

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
