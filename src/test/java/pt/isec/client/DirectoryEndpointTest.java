package pt.isec.client;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DirectoryEndpointTest {

    @Test
    void defaultsToLocalDevelopmentEndpoint() {
        DirectoryEndpoint endpoint = DirectoryEndpoint.resolve(Map.of(), new Properties(), Map.of());

        assertEquals("localhost", endpoint.host());
        assertEquals(9999, endpoint.port());
    }

    @Test
    void namedArgumentsOverridePropertiesAndEnvironment() {
        Properties properties = new Properties();
        properties.setProperty("quiz.directory.host", "property-host");
        properties.setProperty("quiz.directory.port", "12000");

        DirectoryEndpoint endpoint = DirectoryEndpoint.resolve(
                Map.of("directory-host", "argument-host", "directory-port", "13000"),
                properties,
                Map.of("QUIZ_DIRECTORY_HOST", "environment-host", "QUIZ_DIRECTORY_PORT", "14000")
        );

        assertEquals("argument-host", endpoint.host());
        assertEquals(13000, endpoint.port());
    }

    @Test
    void rejectsInvalidPorts() {
        assertThrows(IllegalArgumentException.class,
                () -> DirectoryEndpoint.resolve(Map.of("directory-port", "0"), new Properties(), Map.of()));
        assertThrows(IllegalArgumentException.class,
                () -> DirectoryEndpoint.resolve(Map.of("directory-port", "not-a-number"), new Properties(), Map.of()));
    }
}
