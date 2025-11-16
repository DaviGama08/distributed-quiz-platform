    package pt.isec.server.services.auth;

    import pt.isec.common.dto.auth.*;
    import pt.isec.server.model.user.Student;
    import pt.isec.server.model.user.Teacher;
    import pt.isec.server.db.dao.StudentDAO;
    import pt.isec.server.db.dao.TeacherDAO;
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

            // verificar código de registo do docente (comparação direta com o valor de ConfigServices)
            String expectedCode = configServices.getTeachersRegisterHash();
            if (expectedCode == null || expectedCode.isBlank() || !expectedCode.equals(code)) {
                throw new IllegalArgumentException("Código de registo inválido");
            }

            // hash da password
            String passwordHash = PasswordHasher.hashPassword(pw);

            // criar e guardar docente
            Teacher teacher = new Teacher();
            teacher.setName(name);
            teacher.setEmail(email);
            teacher.setPasswordHash(passwordHash);

            long newId = teacherDAO.add(teacher);
            teacher.setId(newId);

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

            long newId = studentDAO.add(student);
            student.setId(newId);

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
        public LoginResponseDTO login(LoginRequestDTO dto) throws Exception {
            if (dto == null)
                throw new IllegalArgumentException("Dados em falta");

            String email = dto.email();
            String pw    = dto.password();

            if(!Validators.isValidEmail(email))
                throw new IllegalArgumentException("Email inválido");
            if(pw == null || pw.isBlank())
                throw new IllegalArgumentException("Password em falta");

            // 1) procurar docente
            Teacher teacher = teacherDAO.findByEmail(email).orElse(null);
            if(teacher != null){
                if(!PasswordHasher.verifyPassword(pw, teacher.getPasswordHash()))
                    throw new IllegalArgumentException("Credenciais inválidas");

                var session = new SessionServices<Teacher>().create(teacher);
                return new LoginResponseDTO(
                        session.getId(),
                        String.valueOf(teacher.getId()),
                        "TEACHER",
                        teacher.getName(),
                        teacher.getEmail()
                );
            }

            // 2) se não for docente, procurar estudante
            Student student = studentDAO.findByEmail(email).orElse(null);
            if(student != null){
                if(!PasswordHasher.verifyPassword(pw, student.getPasswordHash()))
                    throw new IllegalArgumentException("Credenciais inválidas");

                var session = new SessionServices<Student>().create(student);
                return new LoginResponseDTO(
                        session.getId(),
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
            // TODO implementar troca de senha
        }
    }
