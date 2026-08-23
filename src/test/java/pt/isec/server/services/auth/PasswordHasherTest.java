package pt.isec.server.services.auth;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordHasherTest {

    private static final String TEST_PASSPHRASE = "correct horse battery staple";

    @Test
    void fixedSaltProducesStableVerifiableRepresentation() throws Exception {
        byte[] salt = "0123456789abcdef".getBytes(StandardCharsets.US_ASCII);

        String first = PasswordHasher.hash(TEST_PASSPHRASE, salt, 10_000);
        String second = PasswordHasher.hash(TEST_PASSPHRASE, salt, 10_000);

        assertTrue(PasswordHasher.verify(TEST_PASSPHRASE, first));
        assertFalse(PasswordHasher.verify("wrong password", first));
        assertEquals(first, second);
    }

    @Test
    void productionHashUsesAUniqueSalt() throws Exception {
        String first = PasswordHasher.hash(TEST_PASSPHRASE);
        String second = PasswordHasher.hash(TEST_PASSPHRASE);

        assertNotEquals(first, second);
        assertTrue(PasswordHasher.verify(TEST_PASSPHRASE, first));
        assertTrue(PasswordHasher.verify(TEST_PASSPHRASE, second));
    }

    @Test
    void malformedRepresentationsFailClosed() {
        assertThrows(IllegalArgumentException.class,
                () -> PasswordHasher.verify(TEST_PASSPHRASE, "not-a-pbkdf2-value"));
        assertThrows(IllegalArgumentException.class,
                () -> PasswordHasher.verify(TEST_PASSPHRASE, "0:c2FsdHNhbHRzYWx0c2FsdA==:aGFzaA=="));
    }
}
