package pt.isec.server.services.auth;

import pt.isec.common.dto.auth.*;
import pt.isec.common.model.user.Student;
import pt.isec.common.model.user.Teacher;
import pt.isec.server.repositories.dao.StudentDAO;
import pt.isec.server.repositories.dao.TeacherDAO;
import pt.isec.server.services.session.SessionServices;
import pt.isec.server.services.config.ConfigServices;

public class AuthService implements IAuthService {
    private final TeacherDAO teacherDAO;
    private final StudentDAO studentDAO;
    private final ConfigServices configServices = new ConfigServices();

    public AuthService(TeacherDAO teacherDAO, StudentDAO studentDAO) {
        this.teacherDAO = teacherDAO;
        this.studentDAO = studentDAO;
    }

    @Override
    public LoginResponseDTO registerTeacher(RegisterTeacherDTO dto) throws Exception {
        if (dto == null) throw new IllegalArgumentException("Dados em falta");

        String name  = dto.name();
        String email = dto.email();
        String pw    = dto.password();
        String code  = dto.teacherRegisterCode();

        // validações básicas
        if (!Validators.isValidName(name))   throw new IllegalArgumentException("Nome inválido");
        if (!Validators.isValidEmail(email)) throw new IllegalArgumentException("Email inválido");
        if (!Validators.isValidPassword(pw)) throw new IllegalArgumentException("Password fraca");
        if (code == null || code.isBlank())  throw new IllegalArgumentException("Código de registo obrigatório");

        // email único
        if (teacherDAO.existsByEmail(email) || studentDAO.existsByEmail(email))
            throw new IllegalArgumentException("Email já existe");

        // verificar código de registo do docente
        String storedHash = configServices.getTeachersRegisterHash();
        if (storedHash == null || storedHash.isBlank()
                || !PasswordHasher.verifyPassword(code, storedHash))
            throw new IllegalArgumentException("Código de registo inválido");

        // hash da password
        String passwordHash = PasswordHasher.hashPassword(pw);

        // criar e guardar docente
        Teacher teacher = new Teacher();
        teacher.setName(name);
        teacher.setEmail(email);
        teacher.setPasswordHash(passwordHash);

        long newId = teacherDAO.add(teacher);
        teacher.setId((int) newId);

        // criar sessão
        var session = new SessionServices<Teacher>().create(teacher);

        // resposta
        return new LoginResponseDTO(
                session.getId(),
                String.valueOf(teacher.getId()),
                "TEACHER",
                teacher.getName(),
                teacher.getEmail()
        );
    }

    @Override
    public LoginResponseDTO registerStudent(RegisterStudentDTO dto) throws Exception {
        if (dto == null) throw new IllegalArgumentException("Dados em falta");

        String  name   = dto.name();
        String  email  = dto.email();
        String  pw     = dto.password();
        Integer number = dto.studentNumber();

        if (!Validators.isValidName(name))   throw new IllegalArgumentException("Nome inválido");
        if (!Validators.isValidEmail(email)) throw new IllegalArgumentException("Email inválido");
        if (!Validators.isValidPassword(pw)) throw new IllegalArgumentException("Password fraca");
        if (number == null || number.intValue() <= 0)
            throw new IllegalArgumentException("Número de estudante obrigatório e positivo");

        if (teacherDAO.existsByEmail(email) || studentDAO.existsByEmail(email))
            throw new IllegalArgumentException("Email já existe");

        String passwordHash = PasswordHasher.hashPassword(pw);

        Student student = new Student();
        student.setName(name);
        student.setEmail(email);
        student.setPasswordHash(passwordHash);
        student.setStudentNumber(number);

        studentDAO.add(student);

        var session = new SessionServices<Student>().create(student);

        return new LoginResponseDTO(
                session.getId(),
                String.valueOf(number),
                "STUDENT",
                student.getName(),
                student.getEmail()
        );
    }

    @Override
    public LoginResponseDTO login(LoginRequestDTO loginRequestDTO) {
        // TODO implementar login
        return null;
    }

    @Override
    public void changePassword(ChangePasswordDTO changePasswordDTO) {
        // TODO implementar troca de senha
    }
}
