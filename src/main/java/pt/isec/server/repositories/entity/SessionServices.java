package pt.isec.server.repositories.entity;

public class SessionServices <T>{

    public Session create(T user){
        return new Session();
    }
}
