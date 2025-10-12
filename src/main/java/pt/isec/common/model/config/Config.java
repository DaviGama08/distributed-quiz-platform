package pt.isec.common.model.config;

import java.util.Objects;

public class Config {
    private int dbVersion; // versão da BD
    private String teachersRegisterHash; // hash do código único de docentes

    //gets/sets
    public int getDbVersion() {return dbVersion;}
    public void setDbVersion(int dbVersion) {this.dbVersion = dbVersion;}
    public String getTeachersRegisterHash() {return teachersRegisterHash;}
    public void setTeachersRegisterHash(String teachersRegisterHash) {this.teachersRegisterHash = teachersRegisterHash;}

    //equals/hashCode
    @Override
    public boolean equals(Object o) {
        if(o == this) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Config config = (Config) o;
        return dbVersion == config.dbVersion &&
                Objects.equals(teachersRegisterHash, config.teachersRegisterHash);
    }
    @Override
    public int hashCode() {return Objects.hash(dbVersion, teachersRegisterHash);}
}
