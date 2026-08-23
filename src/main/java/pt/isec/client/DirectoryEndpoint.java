package pt.isec.client;

import java.util.Map;
import java.util.Objects;
import java.util.Properties;

/**
 * Validated directory-service endpoint used by the desktop client.
 *
 * <p>Configuration precedence is: named JavaFX argument, JVM system property,
 * environment variable, then the local-development default.</p>
 */
public record DirectoryEndpoint(String host, int port) {

    static final String DEFAULT_HOST = "localhost";
    static final int DEFAULT_PORT = 9999;

    public DirectoryEndpoint {
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("Directory host must not be blank");
        }
        host = host.trim();
        if (port < 1 || port > 65_535) {
            throw new IllegalArgumentException("Directory port must be between 1 and 65535");
        }
    }

    static DirectoryEndpoint resolve(Map<String, String> namedArguments,
                                     Properties systemProperties,
                                     Map<String, String> environment) {
        Objects.requireNonNull(namedArguments, "namedArguments");
        Objects.requireNonNull(systemProperties, "systemProperties");
        Objects.requireNonNull(environment, "environment");

        String host = firstNonBlank(
                namedArguments.get("directory-host"),
                systemProperties.getProperty("quiz.directory.host"),
                environment.get("QUIZ_DIRECTORY_HOST"),
                DEFAULT_HOST
        );
        String portText = firstNonBlank(
                namedArguments.get("directory-port"),
                systemProperties.getProperty("quiz.directory.port"),
                environment.get("QUIZ_DIRECTORY_PORT"),
                Integer.toString(DEFAULT_PORT)
        );

        try {
            return new DirectoryEndpoint(host, Integer.parseInt(portText));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Directory port must be numeric", exception);
        }
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        throw new IllegalStateException("No configuration value available");
    }
}
