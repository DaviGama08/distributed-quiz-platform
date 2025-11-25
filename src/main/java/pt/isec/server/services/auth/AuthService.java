package pt.isec.server.services.auth;

import pt.isec.common.dto.auth.*;
import pt.isec.server.db.DbCommands;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

/**
 * Serviço de autenticação que não utiliza DAOs.  Acesso directo à BD.
 */
public class AuthService implements IAuthService {

    private final DbCommands dbCommands;
    private static final int ITERATIONS = 210_000;
    private static final int KEY_LENGTH  = 256;
    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";

    private volatile String teacherCodeHashCache;

    public AuthService(DbCommands dbCommands) {
        this.dbCommands = dbCommands;
    }

    @Override
    public AuthResponseDTO registerTeacher(RegisterTeacherDTO dto) throws Exception {
        if (dto == null) throw new IllegalArgumentException("Dados em falta");

        String name  = dto.name();
        String email = dto.email();
        String pw    = dto.password();
        String code  = dto.teacherRegisterCode();

        if (!isValidName(name))   throw new IllegalArgumentException("Nome inválido");
        if (!isValidEmail(email)) throw new IllegalArgumentException("Email inválido");
        if (!isValidPassword(pw)) throw new IllegalArgumentException("Password fraca");
        if (code == null || code.isBlank()) throw new IllegalArgumentException("Código obrigatório");

        // verifica email
        Map<String,Object> exists = dbCommands.selectOne(
                "SELECT 1 FROM teacher WHERE email = ? UNION SELECT 1 FROM student WHERE email = ? LIMIT 1",
                email, email
        );
        if (exists != null) throw new IllegalArgumentException("Email já existe");

        // valida código
        String storedHash = loadTeacherCodeHashFromDb();
        if (!verifyPassword(code, storedHash)) {
            throw new IllegalArgumentException("Código de registo inválido");
        }

        String hashPw = hashPassword(pw);

        // inserir teacher
        long newId;
        dbCommands.runInTransaction(tx -> {
            tx.executeUpdate(
                    "INSERT INTO teacher (name, email, password_hash, created_at) VALUES (?, ?, ?, datetime('now'))",
                    name, email, hashPw
            );
        });
        //Obtem id do professor
        Map<String,Object> row = dbCommands.selectOne("SELECT id FROM teacher where email = ?", email);
        newId = row == null ? -1L : ((Number) row.get("id")).longValue();

        System.out.println("[AUTH SERVICE] newID: " + newId);
        String session = newSessionId();
        return new AuthResponseDTO(session, String.valueOf(newId), "TEACHER", name, email);
    }

    @Override
    public AuthResponseDTO registerStudent(RegisterStudentDTO dto) throws Exception {
        if (dto == null) throw new IllegalArgumentException("Dados em falta");

        String name   = dto.name();
        String email  = dto.email();
        String pw     = dto.password();
        Integer number = dto.studentNumber();

        if (!isValidName(name))   throw new IllegalArgumentException("Nome inválido");
        if (!isValidEmail(email)) throw new IllegalArgumentException("Email inválido");
        if (!isValidPassword(pw)) throw new IllegalArgumentException("Password fraca");
        if (number == null || number <= 0) throw new IllegalArgumentException("Número de estudante inválido");

        Map<String,Object> exists = dbCommands.selectOne(
                "SELECT 1 FROM teacher WHERE email = ? UNION SELECT 1 FROM student WHERE email = ? LIMIT 1",
                email, email
        );
        if (exists != null) throw new IllegalArgumentException("Email já existe");

        String hashPw = hashPassword(pw);

        long newId;
        if (number > 0) {
            dbCommands.executeUpdate(
                    "INSERT INTO student (student_number, name, email, password_hash, created_at) VALUES (?, ?, ?, ?, datetime('now'))",
                    number, name, email, hashPw
            );
            newId = number;
        } else {
            dbCommands.runInTransaction(tx -> {
                tx.executeUpdate(
                        "INSERT INTO student (name, email, password_hash, created_at) VALUES (?, ?, ?, datetime('now'))",
                        name, email, hashPw
                );
            });
            Map<String,Object> r = dbCommands.selectOne("SELECT id FROM student where email = ?", email);
            newId = r == null ? -1L : ((Number) r.get("id")).longValue();
        }

        String session = newSessionId();
        return new AuthResponseDTO(session, String.valueOf(newId), "STUDENT", name, email);
    }

    @Override
    public AuthResponseDTO login(LoginRequestDTO dto) throws Exception {
        if (dto == null) throw new IllegalArgumentException("Dados em falta");
        String email = dto.email();
        String pw    = dto.password();

        if (!isValidEmail(email)){
            throw new IllegalArgumentException("Email inválido");
        }

        if (pw == null || pw.isBlank()) throw new IllegalArgumentException("Password em falta");

        Map<String,Object> teacher = dbCommands.selectOne(
                "SELECT id, name, password_hash FROM teacher WHERE email = ? LIMIT 1",
                email
        );
        if (teacher != null) {
            String stored = (String) teacher.get("password_hash");
            if (!verifyPassword(pw, stored)) throw new IllegalArgumentException("Credenciais inválidas");
            String session = newSessionId();
            return new AuthResponseDTO(session,
                    String.valueOf(((Number) teacher.get("id")).longValue()),
                    "TEACHER", (String) teacher.get("name"), email);
        }

        Map<String,Object> student = dbCommands.selectOne(
                "SELECT student_number, name, password_hash FROM student WHERE email = ? LIMIT 1",
                email
        );
        if (student != null) {
            String stored = (String) student.get("password_hash");
            if (!verifyPassword(pw, stored)) throw new IllegalArgumentException("Credenciais inválidas");
            String session = newSessionId();
            return new AuthResponseDTO(session,
                    String.valueOf(((Number) student.get("student_number")).longValue()),
                    "STUDENT", (String) student.get("name"), email);
        }

        throw new IllegalArgumentException("Credenciais inválidas");
    }

    @Override
    public void changePassword(ChangePasswordDTO changePasswordDTO) {
        if (changePasswordDTO == null) throw new IllegalArgumentException("Dados em falta");
        String userType = changePasswordDTO.userType();
        String id       = String.valueOf(changePasswordDTO.sessionId());
        String oldPass  = changePasswordDTO.oldPassword();
        String newPass  = changePasswordDTO.newPassword();

        if (userType == null || id == null || oldPass == null || newPass == null)
            throw new IllegalArgumentException("Dados em falta");

        if (!isValidPassword(newPass)) {
            throw new IllegalArgumentException("Nova password inválida");
        }
        try {
            if ("TEACHER".equalsIgnoreCase(userType)) {
                Map<String,Object> rec = dbCommands.selectOne(
                        "SELECT password_hash FROM teacher WHERE id = ?", Long.parseLong(id));
                if (rec == null) throw new IllegalArgumentException("Utilizador não encontrado");
                String stored = (String) rec.get("password_hash");
                if (!verifyPassword(oldPass, stored)) {
                    throw new IllegalArgumentException("Password antiga incorreta");
                }
                String newHash = hashPassword(newPass);
                dbCommands.executeUpdate("UPDATE teacher SET password_hash = ? WHERE id = ?",
                        newHash, Long.parseLong(id));

            } else if ("STUDENT".equalsIgnoreCase(userType)) {
                Map<String,Object> rec = dbCommands.selectOne(
                        "SELECT password_hash FROM student WHERE student_number = ?", Long.parseLong(id));
                if (rec == null) throw new IllegalArgumentException("Utilizador não encontrado");
                String stored = (String) rec.get("password_hash");
                if (!verifyPassword(oldPass, stored)) {
                    throw new IllegalArgumentException("Password antiga incorreta");
                }
                String newHash = hashPassword(newPass);
                dbCommands.executeUpdate("UPDATE student SET password_hash = ? WHERE student_number = ?",
                        newHash, Long.parseLong(id));

            } else {
                throw new IllegalArgumentException("Tipo de utilizador inválido");
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void updateStudent(UpdateStudentDTO dto) throws Exception {
        if (dto == null) throw new IllegalArgumentException("Dados em falta");
        Integer userId = dto.userId();
        Integer studentNumber = dto.studentNumber();
        String name = dto.name();
        String email = dto.email();
        String oldPw = dto.oldPassword();
        String newPw = dto.newPassword();

        if (studentNumber == null || studentNumber <= 0) throw new IllegalArgumentException("Número de estudante inválido");
        if (!isValidName(name)) throw new IllegalArgumentException("Nome inválido");
        if (!isValidEmail(email)) throw new IllegalArgumentException("Email inválido");

        // Verificar se email já existe noutro registo (teacher OR student with different student_number)
        Map<String,Object> exists = dbCommands.selectOne(
                "SELECT student_number FROM student WHERE email = ? LIMIT 1", email
        );
        if (exists != null) {
            Integer other = ((Number) exists.get("student_number")).intValue();
            if (!other.equals(studentNumber)) throw new IllegalArgumentException("Email já existe");
        }
        Map<String,Object> teacherWithEmail = dbCommands.selectOne(
                "SELECT id FROM teacher WHERE email = ? LIMIT 1", email
        );
        if (teacherWithEmail != null) throw new IllegalArgumentException("Email já existe");

        // Verificar se student_number colide com outro student (only if changing number)
        Map<String,Object> rec = dbCommands.selectOne("SELECT student_number FROM student WHERE student_number = ?", studentNumber);
        // If rec null => odd, but we assume student exists. We'll update by student_number.

        // Se alterar password, verificar oldPw
        if (newPw != null && !newPw.isBlank()) {
            if (!isValidPassword(newPw)) throw new IllegalArgumentException("Nova password inválida");
            Map<String,Object> current = dbCommands.selectOne("SELECT password_hash FROM student WHERE student_number = ?", studentNumber);
            if (current == null) throw new IllegalArgumentException("Utilizador não encontrado");
            String stored = (String) current.get("password_hash");
            if (oldPw == null || !verifyPassword(oldPw, stored)) throw new IllegalArgumentException("Password antiga incorreta");
            String newHash = hashPassword(newPw);
            dbCommands.executeUpdate("UPDATE student SET password_hash = ?, name = ?, email = ? WHERE student_number = ?",
                    newHash, name, email, studentNumber);
        } else {
            // apenas atualizar nome/email
            dbCommands.executeUpdate("UPDATE student SET name = ?, email = ? WHERE student_number = ?",
                    name, email, studentNumber);
        }
    }

    @Override
    public void updateTeacher(UpdateTeacherDTO dto) throws Exception {
        if (dto == null) throw new IllegalArgumentException("Dados em falta");
        Integer teacherId = dto.teacherId();
        String name = dto.name();
        String email = dto.email();
        String oldPw = dto.oldPassword();
        String newPw = dto.newPassword();

        if (teacherId == null || teacherId <= 0) throw new IllegalArgumentException("ID do docente inválido");
        if (!isValidName(name)) throw new IllegalArgumentException("Nome inválido");
        if (!isValidEmail(email)) throw new IllegalArgumentException("Email inválido");

        // Verificar email não exista noutro teacher or student
        Map<String,Object> exists = dbCommands.selectOne("SELECT id FROM teacher WHERE email = ? LIMIT 1", email);
        if (exists != null) {
            int other = ((Number) exists.get("id")).intValue();
            if (other != teacherId.intValue()) throw new IllegalArgumentException("Email já existe");
        }
        Map<String,Object> studentWithEmail = dbCommands.selectOne("SELECT student_number FROM student WHERE email = ? LIMIT 1", email);
        if (studentWithEmail != null) throw new IllegalArgumentException("Email já existe");

        if (newPw != null && !newPw.isBlank()) {
            if (!isValidPassword(newPw)) throw new IllegalArgumentException("Nova password inválida");
            Map<String,Object> current = dbCommands.selectOne("SELECT password_hash FROM teacher WHERE id = ?", teacherId);
            if (current == null) throw new IllegalArgumentException("Utilizador não encontrado");
            String stored = (String) current.get("password_hash");
            if (oldPw == null || !verifyPassword(oldPw, stored)) throw new IllegalArgumentException("Password antiga incorreta");
            String newHash = hashPassword(newPw);
            dbCommands.executeUpdate("UPDATE teacher SET password_hash = ?, name = ?, email = ? WHERE id = ?",
                    newHash, name, email, teacherId);
        } else {
            dbCommands.executeUpdate("UPDATE teacher SET name = ?, email = ? WHERE id = ?",
                    name, email, teacherId);
        }
    }

    /* --------------------- Métodos auxiliares ------------------------- */

    private String newSessionId() {
        return UUID.randomUUID() + ":" + Instant.now().toEpochMilli();
    }

    // Carrega teacher_code_hash da config, com cache
    public String loadTeacherCodeHashFromDb() {
        String cached = teacherCodeHashCache;
        if (cached != null) return cached;

        synchronized (this) {
            if (teacherCodeHashCache != null) return teacherCodeHashCache;

            Map<String,Object> row = dbCommands.selectOne(
                    "SELECT teacher_code_hash FROM config WHERE id = 1"
            );
            if (row == null) throw new IllegalStateException("Registo de config não encontrado");
            String hash = (String) row.get("teacher_code_hash");
            if (hash == null || hash.isBlank())
                throw new IllegalStateException("teacher_code_hash em branco");
            teacherCodeHashCache = hash;
            return hash;
        }
    }

    /* Validações de campos */
    private static boolean isValidPassword(String password) {
        return password != null &&
                password.matches("^(?=.*[A-Za-z])(?=.*\\d)(?=.*[^A-Za-z0-9]).{8,}$");
    }
    private static boolean isValidEmail(String email) {
        return email != null &&
                email.matches("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    }
    private static boolean isValidName(String name) {
        return name != null &&
                name.matches("^[A-Za-zÀ-ÖØ-öø-ÿ]+(?: [A-Za-zÀ-ÖØ-öø-ÿ]+)*$");
    }

    /* Hash PBKDF2 */
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
    private static boolean verifyPassword(String password, String stored) throws Exception {
        String[] parts = stored.split(":");
        if (parts.length != 3) throw new IllegalArgumentException("Formato de hash inválido");
        int iterations = Integer.parseInt(parts[0]);
        byte[] salt = Base64.getDecoder().decode(parts[1]);
        byte[] hash = Base64.getDecoder().decode(parts[2]);
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterations, hash.length * 8);
        SecretKeyFactory factory = SecretKeyFactory.getInstance(ALGORITHM);
        byte[] testHash = factory.generateSecret(spec).getEncoded();
        if (hash.length != testHash.length) return false;
        int diff = 0;
        for (int i = 0; i < hash.length; i++) {
            diff |= hash[i] ^ testHash[i];
        }
        return diff == 0;
    }
}
