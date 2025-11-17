package pt.isec.server.services.auth;
import pt.isec.common.dto.auth.*;
import pt.isec.server.db.Db;
import pt.isec.server.db.dao.StudentDAO;
import pt.isec.server.db.dao.TeacherDAO;
import pt.isec.server.model.user.Student;
import pt.isec.server.model.user.Teacher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

public class AuthService implements IAuthService {

    /* ========================= DEPENDÊNCIAS ========================= */

    private final Db db;
    private final TeacherDAO teacherDAO;
    private final StudentDAO studentDAO;

    /* ========================= PBKDF2 CONFIG ========================= */

    private static final int ITERATIONS = 210_000;
    private static final int KEY_LENGTH = 256; // bits
    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";

    /* ========================= CACHE CONFIG ========================= */

    // cache da hash do código de docente lida da tabela config
    private volatile String teacherCodeHashCache;

    //só recebe Db, cria as DAOs cá dentro
    public AuthService(Db db) {
        this.db = db;
        this.teacherDAO = new TeacherDAO(db);
        this.studentDAO = new StudentDAO(db);
    }

    /* ========================= IAuthService ========================= */

    @Override
    public LoginResponseDTO registerTeacher(RegisterTeacherDTO dto) throws Exception {
        if (dto == null)
            throw new IllegalArgumentException("Dados em falta");

        String name  = dto.name();
        String email = dto.email();
        String pw    = dto.password();
        String code  = dto.teacherRegisterCode();

        // validações básicas
        if (!isValidName(name))
            throw new IllegalArgumentException("Nome inválido");
        if (!isValidEmail(email))
            throw new IllegalArgumentException("Email inválido");
        if (!isValidPassword(pw))
            throw new IllegalArgumentException("Password fraca");
        if (code == null || code.isBlank())
            throw new IllegalArgumentException("Código de registo obrigatório");

        // email único (docente + estudante)
        if (teacherDAO.existsByEmail(email) || studentDAO.existsByEmail(email))
            throw new IllegalArgumentException("Email já existe");

        // validar código de registo comparando com a hash na BD (tabela config)
        String storedHash = loadTeacherCodeHashFromDb();
        if (storedHash == null || storedHash.isBlank()) {
            throw new IllegalStateException("teacher_code_hash não configurado na tabela config");
        }
        if (!verifyPassword(code, storedHash)) {
            throw new IllegalArgumentException("Código de registo inválido");
        }

        // hash da password do docente
        String passwordHash = hashPassword(pw);

        // criar e guardar docente
        Teacher teacher = new Teacher();
        teacher.setName(name);
        teacher.setEmail(email);
        teacher.setPasswordHash(passwordHash);

        long newId = teacherDAO.add(teacher);
        teacher.setId(newId);

        // gerar id de sessão (não persistimos por agora; podes usar a tabela session no futuro)
        String sessionId = newSessionId();

        return new LoginResponseDTO(
                sessionId,
                String.valueOf(teacher.getId()),
                "TEACHER",
                teacher.getName(),
                teacher.getEmail()
        );
    }

    @Override
    public LoginResponseDTO registerStudent(RegisterStudentDTO dto) throws Exception {
        if (dto == null)
            throw new IllegalArgumentException("Dados em falta");

        String  name   = dto.name();
        String  email  = dto.email();
        String  pw     = dto.password();
        Integer number = dto.studentNumber();

        if (!isValidName(name))
            throw new IllegalArgumentException("Nome inválido");
        if (!isValidEmail(email))
            throw new IllegalArgumentException("Email inválido");
        if (!isValidPassword(pw))
            throw new IllegalArgumentException("Password fraca");
        if (number == null || number.intValue() <= 0)
            throw new IllegalArgumentException("Número de estudante obrigatório e positivo");

        if (teacherDAO.existsByEmail(email) || studentDAO.existsByEmail(email))
            throw new IllegalArgumentException("Email já existe");

        String passwordHash = hashPassword(pw);

        Student student = new Student();
        student.setName(name);
        student.setEmail(email);
        student.setPasswordHash(passwordHash);
        student.setStudentNumber(number);

        long newId = studentDAO.add(student);
        student.setId(newId);

        String sessionId = newSessionId();

        return new LoginResponseDTO(
                sessionId,
                String.valueOf(number),
                "STUDENT",
                student.getName(),
                student.getEmail()
        );
    }

    @Override
    public LoginResponseDTO login(LoginRequestDTO dto) throws Exception {
        if (dto == null)
            throw new IllegalArgumentException("Dados em falta");

        String email = dto.email();
        String pw    = dto.password();

        if (!isValidEmail(email))
            throw new IllegalArgumentException("Email inválido");
        if (pw == null || pw.isBlank())
            throw new IllegalArgumentException("Password em falta");

        // 1) tentar docente
        Teacher teacher = teacherDAO.findByEmail(email).orElse(null);
        if (teacher != null) {
            if (!verifyPassword(pw, teacher.getPasswordHash()))
                throw new IllegalArgumentException("Credenciais inválidas");

            String sessionId = newSessionId();
            return new LoginResponseDTO(
                    sessionId,
                    String.valueOf(teacher.getId()),
                    "TEACHER",
                    teacher.getName(),
                    teacher.getEmail()
            );
        }

        // 2) tentar estudante
        Student student = studentDAO.findByEmail(email).orElse(null);
        if (student != null) {
            if (!verifyPassword(pw, student.getPasswordHash()))
                throw new IllegalArgumentException("Credenciais inválidas");

            String sessionId = newSessionId();
            return new LoginResponseDTO(
                    sessionId,
                    String.valueOf(student.getStudentNumber()),
                    "STUDENT",
                    student.getName(),
                    student.getEmail()
            );
        }

        throw new IllegalArgumentException("Credenciais inválidas");
    }

    @Override
    public void changePassword(ChangePasswordDTO changePasswordDTO) {
        // TODO: implementar se fizer parte da fase
    }

    /* ========================= HELPERS PRIVADOS ========================= */

    // ---- "Sessão" simples (apenas um token gerado) ----
    private String newSessionId() {
        return UUID.randomUUID() + ":" + Instant.now().toEpochMilli();
    }

    // ---- Ler teacher_code_hash da tabela config ----
    public String loadTeacherCodeHashFromDb() {
        String cached = teacherCodeHashCache;
        if (cached != null)
            return cached;

        synchronized (this) {
            if (teacherCodeHashCache != null)
                return teacherCodeHashCache;

            Map<String, Object> row = db.selectOne(
                    "SELECT teacher_code_hash FROM config WHERE id = 1"
            );
            if (row == null)
                throw new IllegalStateException("Registo de config (id=1) não encontrado");

            String hash = (String) row.get("teacher_code_hash");
            if (hash == null || hash.isBlank())
                throw new IllegalStateException("teacher_code_hash em branco na tabela config");

            teacherCodeHashCache = hash;
            return hash;
        }
    }

    // ---- Validações ----

    private static boolean isValidPassword(String password) {
        return password != null && password.matches(
                "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[^A-Za-z0-9]).{8,}$"
        );
    }

    private static boolean isValidEmail(String email) {
        if (email == null) return false;
        String regex = "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$";
        return email.matches(regex);
    }

    private static boolean isValidName(String name) {
        if (name == null || name.isBlank()) return false;
        String regex = "^[A-Za-zÀ-ÖØ-öø-ÿ]+(?: [A-Za-zÀ-ÖØ-öø-ÿ]+)*$";
        return name.matches(regex);
    }

    // ---- PBKDF2 hashing/verificação ----

    private static String hashPassword(String password) throws Exception {
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);

        PBEKeySpec spec = new PBEKeySpec(
                password.toCharArray(),
                salt,
                ITERATIONS,
                KEY_LENGTH
        );

        SecretKeyFactory factory = SecretKeyFactory.getInstance(ALGORITHM);
        byte[] hash = factory.generateSecret(spec).getEncoded();

        String b64Salt = Base64.getEncoder().encodeToString(salt);
        String b64Hash = Base64.getEncoder().encodeToString(hash);

        return ITERATIONS + ":" + b64Salt + ":" + b64Hash;
    }

    private static boolean verifyPassword(String password, String stored) throws Exception {
        String[] parts = stored.split(":");
        if (parts.length != 3)
            throw new IllegalArgumentException("Formato de hash inválido");

        int iterations = Integer.parseInt(parts[0]);
        byte[] salt    = Base64.getDecoder().decode(parts[1]);
        byte[] hash    = Base64.getDecoder().decode(parts[2]);

        PBEKeySpec spec = new PBEKeySpec(
                password.toCharArray(),
                salt,
                iterations,
                hash.length * 8
        );

        SecretKeyFactory factory = SecretKeyFactory.getInstance(ALGORITHM);
        byte[] testHash = factory.generateSecret(spec).getEncoded();

        if (hash.length != testHash.length) return false;
        int diff = 0;
        for (int i = 0; i < hash.length; i++)
            diff |= hash[i] ^ testHash[i];
        return diff == 0;
    }
}
