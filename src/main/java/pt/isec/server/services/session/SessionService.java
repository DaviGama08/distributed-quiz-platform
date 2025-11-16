package pt.isec.server.services.session;
import pt.isec.server.model.user.Student;
import pt.isec.server.model.user.Teacher;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

public class SessionService<T> {
    public Session create(T user) {
        String id = UUID.randomUUID().toString();
        String userId = null; String role = null; String name = null; String email = null;

        if (user instanceof Teacher t) {
            userId = String.valueOf(t.getId());
            role = "TEACHER";
            name = t.getName();
            email = t.getEmail();
        } else if (user instanceof Student st) {
            userId = String.valueOf(st.getStudentNumber());
            role = "STUDENT";
            name = st.getName();
            email = st.getEmail();
        }

        Instant now = Instant.now();
        return new Session(id, userId, role, name, email, now, now.plus(Duration.ofHours(1)));
    }
}


