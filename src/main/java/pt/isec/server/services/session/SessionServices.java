package pt.isec.server.services.session;

public class SessionServices <T>{

    public Session create(T user){
        return new Session();
    }
}
