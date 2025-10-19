package pt.isec;

import pt.isec.common.model.user.Student;
import pt.isec.common.model.user.Teacher;
import pt.isec.server.repositories.SQLiteConnectionFactory;
import pt.isec.server.repositories.SchemaCreator;
import pt.isec.server.repositories.StudentDAO;
import pt.isec.server.repositories.TeacherDAO;

public class MainApp {
    public static void main(String[] args) throws Exception {
        // 1) conexão + criar BD/tabelas se não existirem
        var factory = new SQLiteConnectionFactory("data/app.sqlite");
        new SchemaCreator(factory).ensureSchema();

        // 2) DAOs
        var teacherDAO = new TeacherDAO(factory);
        var studentDAO = new StudentDAO(factory);

        // 3) Inserts de teste (ajusta construtores conforme os teus modelos)
        // Teacher(id é autogerado)
        var t = new Teacher(0, "Ana Silva", "ana@isec.pt", "hash_da_password");
        long newId = teacherDAO.add(t);
        System.out.println("Teacher criado com id=" + newId);

        // Student (student_number é a PK definida por ti)
        var s = new Student(20250001, "João Costa", "joao@isec.pt", "hash_da_password");
        studentDAO.add(s);
        System.out.println("Student criado com número=" + s.getStudentNumber());

        // 4) Ler e imprimir
        var allTeachers = teacherDAO.findAll();
        System.out.println("Teachers: " + allTeachers.size());
        var oneStudent = studentDAO.findById("20250001");
        System.out.println("Student 20250001 existe? " + oneStudent.isPresent());
    }
}
