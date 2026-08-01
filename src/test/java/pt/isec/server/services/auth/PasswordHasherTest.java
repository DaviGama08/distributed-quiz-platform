package pt.isec.server.services.auth;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordHasherTest {

    private static final String VALID_PASSWORD = "correct horse battery staple";

    @Test
    void fixedSaltProducesStableVerifiableRepresentation() throws Exception {
        byte[] salt = "0123456789abcdef".getBytes(StandardCharsets.US_ASCII);

        String first = PasswordHasher.hash(VALID_PASSWORD, salt, 10_000);
        String second = PasswordHasher.hash(VALID_PASSWORD, salt, 10_000);

        assertTrue(PasswordHasher.verify(VALID_PASSWORD, first));
        assertFalse(PasswordHasher.verify("wrong password", first));
        assertEquals(first, second);
    }

    @Test
    void productionHashUsesAUniqueSalt() throws Exception {
        String first = PasswordHasher.hash(VALID_PASSWORD);
        String second = PasswordHasher.hash(VALID_PASSWORD);

        assertNotEquals(first, second);
        assertTrue(PasswordHasher.verify(VALID_PASSWORD, first));
        assertTrue(PasswordHasher.verify(VALID_PASSWORD, second));
    }

    @Test
    void malformedRepresentationsFailClosed() {
        assertThrows(IllegalArgumentException.class,
                () -> PasswordHasher.verify(VALID_PASSWORD, "not-a-pbkdf2-value"));
        assertThrows(IllegalArgumentException.class,
                () -> PasswordHasher.verify(VALID_PASSWORD, "0:c2FsdHNhbHRzYWx0c2FsdA==:aGFzaA=="));
    }
}
