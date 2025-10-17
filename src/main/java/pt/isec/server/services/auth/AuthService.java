package pt.isec.server.services.auth;

import pt.isec.common.dto.auth.*;
import pt.isec.common.model.user.Student;
import pt.isec.common.model.user.Teacher;
import pt.isec.server.repositories.entity.SessionServices;
import pt.isec.server.repositories.StudentDAO;
import pt.isec.server.repositories.TeacherDAO;
import pt.isec.server.services.config.ConfigServices;

import java.sql.SQLException;

public class AuthService implements  IAuthService{
    private final TeacherDAO teacherDAO;
    private final StudentDAO studentDAO;

    private final SessionServices sessionServices;

    private final ConfigServices configServices;
    public AuthService(TeacherDAO teacherDAO, StudentDAO studentDAO, SessionServices sessionServices,
                       ConfigServices configServices) {
        this.teacherDAO = teacherDAO;
        this.studentDAO = studentDAO;
        this.sessionServices = sessionServices;
        this.configServices = configServices;
    }


    @Override
    public LoginResponseDTO registerTeacher(RegisterTeacherDTO registerTeacherDTO) throws SQLException {
        //gerar o hash da password e verificar se é válido.
        // ...

        //verificar email e name ...
        // ...


        Teacher t = new Teacher();
        t.setName(registerTeacherDTO.name());
        t.setEmail(registerTeacherDTO.email());
        t.setPasswordHash(registerTeacherDTO.password()); // temos que fazer uma classe para gerar o hash da password
        teacherDAO.add(t);

        // autenticação
        // ...

        var session = sessionServices.create(t);

        //No retorno abaixo, em vez de fornecermos os dados do objeto Teacher, forneceremos de um objeto Autentication,
        //ou seja: t.getId() ----> auth.getId().
        return new LoginResponseDTO(session.getId(), t.getId(), "TEACHER", t.getName(), t.getEmail());
    }

    @Override
    public LoginResponseDTO registerStudent(RegisterStudentDTO registerStudentDTO) throws SQLException {
        //gerar o hash da password e verificar se é válido.
        // ...

        //verificar email e name ...
        // ...


        Student s = new Student();
        s.setName(registerStudentDTO.name());
        s.setEmail(registerStudentDTO.email());
        s.setPasswordHash(registerStudentDTO.password()); // temos que fazer uma classe para gerar o hash da password
        studentDAO.add(s);

        // autenticação
        // ...

        var session = sessionServices.create(s);

        //No retorno abaixo, em vez de fornecermos os dados do objeto Student, forneceremos de um objeto Autentication,
        //ou seja: s.getId() ----> auth.getId().
        return new LoginResponseDTO(session.getId(), s.getStudentNumber(), "STUDENT", s.getName(), s.getEmail());
    }

    @Override
    public LoginResponseDTO login(LoginRequestDTO loginRequestDTO) {
        return null;
    }

    @Override
    public void changePassword(ChangePasswordDTO changePasswordDTO) {

    }
}
