package pt.isec.server.services.session;
import pt.isec.server.model.user.Student;
import pt.isec.server.model.user.Teacher;

import java.time.Duration;
import java.util.UUID;

public class SessionServices<T> {

    public Session create(T user) {
        Session s = new Session();
        s.setId(UUID.randomUUID().toString());

        if (user instanceof Teacher t) {
            s.setUserId(String.valueOf(t.getId()));
            s.setRole("TEACHER");
            s.setName(t.getName());
            s.setEmail(t.getEmail());
        } else if (user instanceof Student st) {
            s.setUserId(String.valueOf(st.getStudentNumber()));
            s.setRole("STUDENT");
            s.setName(st.getName());
            s.setEmail(st.getEmail());
        }

        java.time.Instant now = java.time.Instant.now();
        s.setCreatedAt(now);
        s.setExpiresAt(now.plus(Duration.ofHours(1)));

        return s;
    }
}
