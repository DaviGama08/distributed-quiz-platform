package pt.isec.server.services.auth;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/** PBKDF2 password hashing independent from database and network code. */
public final class PasswordHasher {

    static final int ITERATIONS = 210_000;
    static final int KEY_LENGTH = 256;
    private static final int SALT_LENGTH = 16;
    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private PasswordHasher() {
    }

    public static String hash(String password) throws GeneralSecurityException {
        requirePassword(password);
        byte[] salt = new byte[SALT_LENGTH];
        SECURE_RANDOM.nextBytes(salt);
        return hash(password, salt, ITERATIONS);
    }

    static String hash(String password, byte[] salt, int iterations) throws GeneralSecurityException {
        requirePassword(password);
        if (salt == null || salt.length < SALT_LENGTH) {
            throw new IllegalArgumentException("Salt must contain at least 16 bytes");
        }
        if (iterations < 1) {
            throw new IllegalArgumentException("Iterations must be positive");
        }

        byte[] derived = derive(password, salt, iterations, KEY_LENGTH);
        return iterations + ":"
                + Base64.getEncoder().encodeToString(salt) + ":"
                + Base64.getEncoder().encodeToString(derived);
    }

    public static boolean verify(String password, String stored) throws GeneralSecurityException {
        requirePassword(password);
        if (stored == null) {
            throw new IllegalArgumentException("Stored hash must not be null");
        }

        String[] parts = stored.split(":", -1);
        if (parts.length != 3) {
            throw new IllegalArgumentException("Invalid password-hash format");
        }

        try {
            int iterations = Integer.parseInt(parts[0]);
            if (iterations < 1) {
                throw new IllegalArgumentException("Invalid iteration count");
            }
            byte[] salt = Base64.getDecoder().decode(parts[1]);
            byte[] expected = Base64.getDecoder().decode(parts[2]);
            if (salt.length < SALT_LENGTH || expected.length == 0) {
                throw new IllegalArgumentException("Invalid password-hash payload");
            }
            byte[] actual = derive(password, salt, iterations, expected.length * Byte.SIZE);
            return MessageDigest.isEqual(expected, actual);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Invalid password-hash format", exception);
        }
    }

    private static byte[] derive(String password, byte[] salt, int iterations, int keyLength)
            throws GeneralSecurityException {
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterations, keyLength);
        try {
            return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).getEncoded();
        } finally {
            spec.clearPassword();
        }
    }

    private static void requirePassword(String password) {
        if (password == null || password.isEmpty()) {
            throw new IllegalArgumentException("Password must not be empty");
        }
    }
}
