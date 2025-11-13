package pt.isec.server.services.auth;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.SecretKeyFactory;
import java.security.SecureRandom;
import java.util.Base64;

public class PasswordHasher {
    private static final int ITERATIONS = 210_000; // número de iterações do PBKDF2 (custo de CPU: quanto maior, mais lento para atacar).
    private static final int KEY_LENGTH = 256;     // tamanho da saída em bits (256 bits).
    private static final String ALGORITHM = "PBKDF2WithHmacSHA256"; // variante do PBKDF2 usando HMAC-SHA-256.

    public static String hashPassword(String password) throws Exception{
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt); // preenche salt com números aleatórios

        PBEKeySpec spec = new PBEKeySpec(
                password.toCharArray(),                 // 1) senha como array de char.
                salt,                                   // 2) sal exclusivo.
                ITERATIONS,                             // 3) custo (nº de iterações).
                KEY_LENGTH                              // 4) tamanho da chave derivada (em bits).
        );

        SecretKeyFactory factory = SecretKeyFactory.getInstance(ALGORITHM);
        byte[] hash = factory.generateSecret(spec).getEncoded();
        String b64Salt = Base64.getEncoder().encodeToString(salt);
        String b64Hash = Base64.getEncoder().encodeToString(hash);
        return ITERATIONS + ":" + b64Salt + ":" + b64Hash;
    }

    //Esse método vai receber uma String ITERATION:SALT_BASE64:HASH_BASE64 e uma password que vai ser convertido para o formato anterior
    //para depois ser comparado byte a byte.
    public static boolean verifyPassword(String password, String stored) throws Exception{
        String[] parts = stored.split(":");
        int iterations = Integer.parseInt(parts[0]);
        byte [] salt   = Base64.getDecoder().decode(parts[1]);
        byte [] hash   = Base64.getDecoder().decode(parts[2]);

        PBEKeySpec spec = new PBEKeySpec(
                password.toCharArray(),                 // 1) senha fornecida agora no login.
                salt,                                   // 2) o mesmo sal que foi usado no cadastro.
                iterations,                             // 3) as mesmas iterações daquele registro.
                hash.length * 8                         // 4) mesmo tamanho de saída (bits) usado antes.
        );

        SecretKeyFactory factory = SecretKeyFactory.getInstance(ALGORITHM);
        byte[] testHash          = factory.generateSecret(spec).getEncoded();

        if(hash.length != testHash.length) return false;
        int diff = 0;

        for (int i = 0; i < hash.length; i++)
            diff |= hash[i] ^ testHash[i]; // faz XOR e OR acumulado (não sai mais rápido quando diverge).
        return diff == 0;
    }
}
