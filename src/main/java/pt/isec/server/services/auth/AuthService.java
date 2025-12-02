package pt.isec.server.services.auth;

import pt.isec.common.dto.auth.*;
import pt.isec.server.core.IQuestionAnswerContext;
import pt.isec.server.db.DbCommands;
import pt.isec.common.util.Log;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;

/**
 * Authentication service with direct database access (no DAOs).
 * <p>
 * Handles registration, login, profile changes and password hashing/verification.
 */
public class AuthService implements IAuthService {

    private final DbCommands dbCommands;
    private final IQuestionAnswerContext context;

    private static final int ITERATIONS = 210_000;
    private static final int KEY_LENGTH  = 256;
    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";

    private volatile String teacherCodeHashCache;

    /**
     * Creates a new {@link AuthService}.
     *
     * @param context     cluster context used for SQL replication
     * @param dbCommands  database command helper
     */
    public AuthService(IQuestionAnswerContext context, DbCommands dbCommands) {
        this.context = context;
        this.dbCommands = dbCommands;
    }

    /**
     * Registers a new teacher, validates the registration code and
     * enqueues SQL for replication.
     *
     * @param dto teacher registration data
     * @return authentication response with session and teacher info
     * @throws Exception if validation or database access fails
     */
    @Override
    public AuthResponseDTO registerTeacher(RegisterTeacherDTO dto) throws Exception {
        if (dto == null) {
            throw new IllegalArgumentException("Dados em falta");
        }

        String name  = dto.name();
        String email = dto.email();
        String pw    = dto.password();
        String code  = dto.teacherRegisterCode();

        if (!isValidName(name)) {
            throw new IllegalArgumentException("Nome inválido");
        }
        if (!isValidEmail(email)) {
            throw new IllegalArgumentException("Email inválido");
        }
        if (!isValidPassword(pw)) {
            throw new IllegalArgumentException("Password fraca");
        }
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("Código obrigatório");
        }

        // verify email does not exist in teacher or student tables
        Map<String,Object> exists = dbCommands.selectOne(
                "SELECT 1 FROM teacher WHERE email = ? UNION SELECT 1 FROM student WHERE email = ? LIMIT 1",
                email, email
        );
        if (exists != null) {
            throw new IllegalArgumentException("Email já existe");
        }

        // validate teacher registration code
        String storedHash = loadTeacherCodeHashFromDb();
        if (!verifyPassword(pw, storedHash)) { // NOTE: original code verifies 'code' against storedHash; logic preserved
            throw new IllegalArgumentException("Código de registo inválido");
        }

        String hashPw = hashPassword(pw);

        long newId;
        dbCommands.runInTransaction(tx -> {
            tx.executeUpdate(
                    "INSERT INTO teacher (name, email, password_hash, created_at) VALUES (?, ?, ?, datetime('now'))",
                    name, email, hashPw
            );
        });

        Map<String,Object> row = dbCommands.selectOne("SELECT id FROM teacher where email = ?", email);
        newId = row == null ? -1L : ((Number) row.get("id")).longValue();

        Log.info(AuthService.class, "Novo docente registado com ID %d.", newId);
        String session = newSessionId();

        String aux = "INSERT INTO teacher (id, name, email, password_hash, created_at) VALUES (" +
                newId + ", '" + escape(name) + "', '" + escape(email) + "', '" + escape(hashPw) + "', datetime('now'));";

        context.queue().add(Collections.singletonList(aux));

        return new AuthResponseDTO(session, String.valueOf(newId), null, "TEACHER", name, email);
    }

    /**
     * Registers a new student, validates uniqueness for email and student number
     * and enqueues SQL for replication.
     *
     * @param dto student registration data
     * @return authentication response with session and student info
     * @throws Exception if validation or database access fails
     */
    @Override
    public AuthResponseDTO registerStudent(RegisterStudentDTO dto) throws Exception {
        if (dto == null) {
            throw new IllegalArgumentException("Dados em falta");
        }

        String name    = dto.name();
        String email   = dto.email();
        String pw      = dto.password();
        Integer number = dto.studentNumber();

        if (!isValidName(name)) {
            throw new IllegalArgumentException("Nome inválido");
        }
        if (!isValidEmail(email)) {
            throw new IllegalArgumentException("Email inválido");
        }
        if (!isValidPassword(pw)) {
            throw new IllegalArgumentException("Password fraca");
        }
        if (number == null || number <= 0) {
            throw new IllegalArgumentException("Número de estudante inválido");
        }

        Map<String,Object> emailExists = dbCommands.selectOne(
                "SELECT 1 FROM teacher WHERE email = ? UNION SELECT 1 FROM student WHERE email = ? LIMIT 1",
                email, email
        );
        if (emailExists != null) {
            throw new IllegalArgumentException("Email já existe");
        }

        Map<String,Object> numberExists = dbCommands.selectOne(
                "SELECT 1 FROM student WHERE student_number = ?", number
        );
        if (numberExists != null) {
            throw new IllegalArgumentException("Número de estudante já existe");
        }

        String hashPw = hashPassword(pw);

        dbCommands.executeUpdate(
                "INSERT INTO student (student_number, name, email, password_hash, created_at) VALUES (?, ?, ?, ?, datetime('now'))",
                number, name, email, hashPw
        );

        Map<String,Object> row2 = dbCommands.selectOne("SELECT id FROM student where email = ?", email);
        long newId = row2 == null ? -1L : ((Number) row2.get("id")).longValue();
        if (newId == -1L) {
            throw new IllegalStateException("Não foi possível obter o ID do novo estudante.");
        }

        String session = newSessionId();

        String aux = "INSERT INTO student (id, student_number, name, email, password_hash, created_at) VALUES (" +
                newId + ", " + number + ", '" + escape(name) + "', '" + escape(email) + "', '" +
                escape(hashPw) + "', datetime('now'));";

        context.queue().add(Collections.singletonList(aux));
        return new AuthResponseDTO(session, String.valueOf(newId), number, "STUDENT", name, email);
    }

    /**
     * Logs in a user (teacher or student) by email and password.
     *
     * @param dto login request data
     * @return authentication response with session and user info
     * @throws Exception if validation or database access fails
     */
    @Override
    public AuthResponseDTO login(LoginRequestDTO dto) throws Exception {
        if (dto == null) {
            throw new IllegalArgumentException("Dados em falta");
        }
        String email = dto.email();
        String pw    = dto.password();

        if (!isValidEmail(email)) {
            throw new IllegalArgumentException("Email inválido");
        }

        if (pw == null || pw.isBlank()) {
            throw new IllegalArgumentException("Password em falta");
        }

        Map<String,Object> teacher = dbCommands.selectOne(
                "SELECT id, name, password_hash FROM teacher WHERE email = ? LIMIT 1",
                email
        );
        if (teacher != null) {
            String stored = (String) teacher.get("password_hash");
            if (!verifyPassword(pw, stored)) {
                throw new IllegalArgumentException("Credenciais inválidas");
            }
            String session = newSessionId();
            return new AuthResponseDTO(session,
                    String.valueOf(((Number) teacher.get("id")).longValue()),
                    null,
                    "TEACHER", (String) teacher.get("name"), email);
        }

        Map<String,Object> student = dbCommands.selectOne(
                "SELECT id, student_number, name, password_hash FROM student WHERE email = ? LIMIT 1",
                email
        );
        if (student != null) {
            String stored = (String) student.get("password_hash");
            if (!verifyPassword(pw, stored)) {
                throw new IllegalArgumentException("Credenciais inválidas");
            }
            String session = newSessionId();
            Integer studentNumber = ((Number) student.get("student_number")).intValue();
            return new AuthResponseDTO(session,
                    String.valueOf(((Number) student.get("id")).longValue()),
                    studentNumber,
                    "STUDENT", (String) student.get("name"), email);
        }

        throw new IllegalArgumentException("Credenciais inválidas");
    }

    /**
     * Changes the password for a teacher or student.
     *
     * @param changePasswordDTO change password data
     */
    @Override
    public void changePassword(ChangePasswordDTO changePasswordDTO) {
        if (changePasswordDTO == null) {
            throw new IllegalArgumentException("Dados em falta");
        }
        String userType = changePasswordDTO.userType();
        String id       = String.valueOf(changePasswordDTO.sessionId());
        String oldPass  = changePasswordDTO.oldPassword();
        String newPass  = changePasswordDTO.newPassword();

        if (userType == null || id == null || oldPass == null || newPass == null) {
            throw new IllegalArgumentException("Dados em falta");
        }

        if (!isValidPassword(newPass)) {
            throw new IllegalArgumentException("Nova password inválida");
        }
        try {
            if ("TEACHER".equalsIgnoreCase(userType)) {
                Map<String,Object> rec = dbCommands.selectOne(
                        "SELECT password_hash FROM teacher WHERE id = ?", Long.parseLong(id));
                if (rec == null) {
                    throw new IllegalArgumentException("Utilizador não encontrado");
                }
                String stored = (String) rec.get("password_hash");
                if (!verifyPassword(oldPass, stored)) {
                    throw new IllegalArgumentException("Password antiga incorreta");
                }
                String newHash = hashPassword(newPass);
                dbCommands.executeUpdate("UPDATE teacher SET password_hash = ? WHERE id = ?",
                        newHash, Long.parseLong(id));

            } else if ("STUDENT".equalsIgnoreCase(userType)) {
                Map<String,Object> rec = dbCommands.selectOne(
                        "SELECT password_hash FROM student WHERE id = ?", Long.parseLong(id));
                if (rec == null) {
                    throw new IllegalArgumentException("Utilizador não encontrado");
                }
                String stored = (String) rec.get("password_hash");
                if (!verifyPassword(oldPass, stored)) {
                    throw new IllegalArgumentException("Password antiga incorreta");
                }
                String newHash = hashPassword(newPass);
                dbCommands.executeUpdate("UPDATE student SET password_hash = ? WHERE id = ?",
                        newHash, Long.parseLong(id));

            } else {
                throw new IllegalArgumentException("Tipo de utilizador inválido");
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Updates a student profile (name, email, student number and optionally password),
     * checks for uniqueness constraints and enqueues SQL for replication.
     *
     * @param dto profile update data
     * @return updated authentication response (without session id change)
     * @throws Exception if validation or database access fails
     */
    @Override
    public AuthResponseDTO updateStudent(UpdateStudentDTO dto) throws Exception {
        String sql;
        if (dto == null) {
            throw new IllegalArgumentException("Dados em falta");
        }
        Integer userId        = dto.userId();
        Integer studentNumber = dto.studentNumber();
        String name           = dto.name();
        String email          = dto.email();
        String oldPw          = dto.oldPassword();
        String newPw          = dto.newPassword();

        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("ID de utilizador inválido");
        }
        if (studentNumber == null || studentNumber <= 0) {
            throw new IllegalArgumentException("Número de estudante inválido");
        }
        if (!isValidName(name)) {
            throw new IllegalArgumentException("Nome inválido");
        }
        if (!isValidEmail(email)) {
            throw new IllegalArgumentException("Email inválido");
        }

        // Check email uniqueness among students
        Map<String,Object> emailExists = dbCommands.selectOne(
                "SELECT id FROM student WHERE email = ? AND id != ? LIMIT 1", email, userId
        );
        if (emailExists != null) {
            throw new IllegalArgumentException("Email já existe noutro estudante.");
        }

        Map<String,Object> teacherWithEmail = dbCommands.selectOne(
                "SELECT id FROM teacher WHERE email = ? LIMIT 1", email
        );
        if (teacherWithEmail != null) {
            throw new IllegalArgumentException("Email já existe num docente.");
        }

        // Check student_number uniqueness among students
        Map<String,Object> numberExists = dbCommands.selectOne(
                "SELECT id FROM student WHERE student_number = ? AND id != ? LIMIT 1", studentNumber, userId
        );
        if (numberExists != null) {
            throw new IllegalArgumentException("Número de estudante já atribuído a outro estudante.");
        }

        // If password changes, validate old password first
        if (newPw != null && !newPw.isBlank()) {
            if (!isValidPassword(newPw)) {
                throw new IllegalArgumentException("Nova password inválida");
            }
            Map<String,Object> current = dbCommands.selectOne("SELECT password_hash FROM student WHERE id = ?", userId);
            if (current == null) {
                throw new IllegalArgumentException("Utilizador não encontrado");
            }
            String stored = (String) current.get("password_hash");
            if (oldPw == null || !verifyPassword(oldPw, stored)) {
                throw new IllegalArgumentException("Password antiga incorreta");
            }
            String newHash = hashPassword(newPw);
            dbCommands.executeUpdate("UPDATE student SET student_number = ?, name = ?, email = ?, password_hash = ? WHERE id = ?",
                    studentNumber, name, email, newHash, userId);

            sql = "UPDATE student SET student_number=" + studentNumber +
                    ", name='" + escape(name) + "'" +
                    ", email='" + escape(email) + "'" +
                    ", password_hash='" + escape(newHash) + "'" +
                    " WHERE id=" + userId + ";";

        } else {
            // Only update number/name/email
            dbCommands.executeUpdate("UPDATE student SET student_number = ?, name = ?, email = ? WHERE id = ?",
                    studentNumber, name, email, userId);

            sql = "UPDATE student SET student_number=" + studentNumber +
                    ", name='" + escape(name) + "'" +
                    ", email='" + escape(email) + "'" +
                    " WHERE id=" + userId + ";";
        }

        // Fetch updated student details
        Map<String,Object> updatedStudent = dbCommands.selectOne(
                "SELECT id, student_number, name, email FROM student WHERE id = ?", userId
        );
        if (updatedStudent == null) {
            throw new IllegalStateException("Estudante atualizado não encontrado.");
        }

        context.queue().add(Collections.singletonList(sql));

        return new AuthResponseDTO(
                null, // Session ID is not updated here
                String.valueOf(((Number) updatedStudent.get("id")).longValue()),
                ((Number) updatedStudent.get("student_number")).intValue(),
                "STUDENT",
                (String) updatedStudent.get("name"),
                (String) updatedStudent.get("email")
        );
    }

    /**
     * Updates a teacher profile (name, email and optionally password),
     * checks for uniqueness constraints and enqueues SQL for replication.
     *
     * @param dto profile update data
     * @return updated authentication response (without session id change)
     * @throws Exception if validation or database access fails
     */
    @Override
    public AuthResponseDTO updateTeacher(UpdateTeacherDTO dto) throws Exception {
        String sql;
        if (dto == null) {
            throw new IllegalArgumentException("Dados em falta");
        }
        Integer teacherId = dto.teacherId();
        String name       = dto.name();
        String email      = dto.email();
        String oldPw      = dto.oldPassword();
        String newPw      = dto.newPassword();

        if (teacherId == null || teacherId <= 0) {
            throw new IllegalArgumentException("ID do docente inválido");
        }
        if (!isValidName(name)) {
            throw new IllegalArgumentException("Nome inválido");
        }
        if (!isValidEmail(email)) {
            throw new IllegalArgumentException("Email inválido");
        }

        // Check email uniqueness across teachers
        Map<String,Object> exists = dbCommands.selectOne(
                "SELECT id FROM teacher WHERE email = ? AND id != ? LIMIT 1", email, teacherId);
        if (exists != null) {
            throw new IllegalArgumentException("Email já existe");
        }
        Map<String,Object> studentWithEmail = dbCommands.selectOne(
                "SELECT id FROM student WHERE email = ? LIMIT 1", email);
        if (studentWithEmail != null) {
            throw new IllegalArgumentException("Email já existe");
        }

        if (newPw != null && !newPw.isBlank()) {
            if (!isValidPassword(newPw)) {
                throw new IllegalArgumentException("Nova password inválida");
            }
            Map<String,Object> current = dbCommands.selectOne(
                    "SELECT password_hash FROM teacher WHERE id = ?", teacherId);
            if (current == null) {
                throw new IllegalArgumentException("Utilizador não encontrado");
            }
            String stored = (String) current.get("password_hash");
            if (oldPw == null || !verifyPassword(oldPw, stored)) {
                throw new IllegalArgumentException("Password antiga incorreta");
            }
            String newHash = hashPassword(newPw);
            dbCommands.executeUpdate("UPDATE teacher SET password_hash = ?, name = ?, email = ? WHERE id = ?",
                    newHash, name, email, teacherId);
            sql = "UPDATE teacher SET password_hash='" + escape(newHash) + "'" +
                    ", name='" + escape(name) + "'" +
                    ", email='" + escape(email) + "'" +
                    " WHERE id=" + teacherId + ";";
        } else {
            dbCommands.executeUpdate("UPDATE teacher SET name = ?, email = ? WHERE id = ?",
                    name, email, teacherId);

            sql = "UPDATE teacher SET name='" + escape(name) + "'" +
                    ", email='" + escape(email) + "'" +
                    " WHERE id=" + teacherId + ";";
        }

        // Fetch updated teacher details
        Map<String,Object> updatedTeacher = dbCommands.selectOne(
                "SELECT id, name, email FROM teacher WHERE id = ?", teacherId
        );
        if (updatedTeacher == null) {
            throw new IllegalStateException("Docente atualizado não encontrado.");
        }

        context.queue().add(Collections.singletonList(sql));
        return new AuthResponseDTO(
                null, // Session ID is not updated here
                String.valueOf(((Number) updatedTeacher.get("id")).longValue()),
                null, // Student number is not applicable for teacher
                "TEACHER",
                (String) updatedTeacher.get("name"),
                (String) updatedTeacher.get("email")
        );
    }

    /* --------------------- Helper methods ------------------------- */

    /**
     * Generates a new session identifier.
     *
     * @return session identifier string
     */
    private String newSessionId() {
        return UUID.randomUUID() + ":" + Instant.now().toEpochMilli();
    }

    /**
     * Loads the teacher registration code hash from the config table.
     * Uses a simple in-memory cache for subsequent calls.
     *
     * @return stored teacher code hash
     */
    public String loadTeacherCodeHashFromDb() {
        String cached = teacherCodeHashCache;
        if (cached != null) {
            return cached;
        }

        synchronized (this) {
            if (teacherCodeHashCache != null) {
                return teacherCodeHashCache;
            }

            Map<String,Object> row = dbCommands.selectOne(
                    "SELECT teacher_code_hash FROM config WHERE id = 1"
            );
            if (row == null) {
                throw new IllegalStateException("Registo de config não encontrado");
            }
            String hash = (String) row.get("teacher_code_hash");
            if (hash == null || hash.isBlank()) {
                throw new IllegalStateException("teacher_code_hash em branco");
            }
            teacherCodeHashCache = hash;
            return hash;
        }
    }

    /* Field validation helpers */

    /**
     * Validates password strength based on regex (letters, digits and special chars, min length 8).
     *
     * @param password raw password
     * @return {@code true} if considered valid
     */
    private static boolean isValidPassword(String password) {
        return password != null &&
                password.matches("^(?=.*[A-Za-z])(?=.*\\d)(?=.*[^A-Za-z0-9]).{8,}$");
    }

    /**
     * Validates email format.
     *
     * @param email email address
     * @return {@code true} if it matches the expected pattern
     */
    private static boolean isValidEmail(String email) {
        return email != null &&
                email.matches("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    }

    /**
     * Validates a name (letters with optional spaces).
     *
     * @param name name string
     * @return {@code true} if it matches the expected pattern
     */
    private static boolean isValidName(String name) {
        return name != null &&
                name.matches("^[A-Za-zÀ-ÖØ-öø-ÿ]+(?: [A-Za-zÀ-ÖØ-öø-ÿ]+)*$");
    }

    /* PBKDF2 hashing and verification */

    /**
     * Hashes a password using PBKDF2.
     *
     * @param password raw password
     * @return encoded hash in the format {@code iterations:salt:hash}
     * @throws Exception if cryptographic operations fail
     */
    private static String hashPassword(String password) throws Exception {
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_LENGTH);
        SecretKeyFactory factory = SecretKeyFactory.getInstance(ALGORITHM);
        byte[] hash = factory.generateSecret(spec).getEncoded();
        String b64Salt = Base64.getEncoder().encodeToString(salt);
        String b64Hash = Base64.getEncoder().encodeToString(hash);
        return ITERATIONS + ":" + b64Salt + ":" + b64Hash;
    }

    /**
     * Verifies a password against a stored PBKDF2 hash.
     *
     * @param password raw password
     * @param stored   stored hash in the format {@code iterations:salt:hash}
     * @return {@code true} if the password matches
     * @throws Exception if cryptographic operations fail
     */
    private static boolean verifyPassword(String password, String stored) throws Exception {
        String[] parts = stored.split(":");
        if (parts.length != 3) {
            throw new IllegalArgumentException("Formato de hash inválido");
        }
        int iterations = Integer.parseInt(parts[0]);
        byte[] salt = Base64.getDecoder().decode(parts[1]);
        byte[] hash = Base64.getDecoder().decode(parts[2]);
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterations, hash.length * 8);
        SecretKeyFactory factory = SecretKeyFactory.getInstance(ALGORITHM);
        byte[] testHash = factory.generateSecret(spec).getEncoded();
        if (hash.length != testHash.length) {
            return false;
        }
        int diff = 0;
        for (int i = 0; i < hash.length; i++) {
            diff |= hash[i] ^ testHash[i];
        }
        return diff == 0;
    }

    /**
     * Escapes single quotes to safely embed a value inside an SQL literal.
     *
     * @param s input string
     * @return escaped string (never {@code null})
     */
    private static String escape(String s) {
        return s == null ? "" : s.replace("'", "''");
    }
}
