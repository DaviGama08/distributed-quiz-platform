package pt.isec.common.model.config;

import java.io.Serializable;
import java.util.Objects;

public class Config implements Serializable {
    private static final long serialVersionUID = 1L;
    private int dbVersion; // versão da BD
    private String teachersRegisterHash; // hash do código único de docentes

    public Config() {}

    public Config(int dbVersion, String teachersRegisterHash) {
        setDbVersion(dbVersion);
        setTeachersRegisterHash(teachersRegisterHash);
    }

    //gets/sets
    public int getDbVersion() {return dbVersion;}
    public void setDbVersion(int dbVersion) {
        if (dbVersion < 0) throw new IllegalArgumentException("dbVersion can't be  less than 0");
        this.dbVersion = dbVersion;
    }
    public String getTeachersRegisterHash() {return teachersRegisterHash;}
    public void setTeachersRegisterHash(String teachersRegisterHash) {this.teachersRegisterHash = teachersRegisterHash;}

    @Override
    public String toString() {
        return "Config{dbVersion=" + dbVersion + ", teachersRegisterHash=" + (teachersRegisterHash == null ? "null" : "'***'") + "}";
    }

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
