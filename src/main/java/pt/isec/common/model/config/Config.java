package pt.isec.common.model.config;

import java.io.Serializable;
import java.util.Objects;

public class Config implements Serializable {
    private static final long serialVersionUID = 1L;
    private int dbVersion; // versão da BD
    private String teachersRegisterHash; // hash do código único de docentes

    public Config() {
        this.dbVersion = 0;
        this.teachersRegisterHash = null;
    }

    public Config(int dbVersion, String teachersRegisterHash) {
        validate(dbVersion, teachersRegisterHash);
        this.dbVersion = dbVersion;
        this.teachersRegisterHash = teachersRegisterHash;
    }

    //gets/sets
    public void setDbVersion(int dbVersion) {
        if (dbVersion < 0)
            throw new IllegalArgumentException("dbVersion cannot be less than 0");
        this.dbVersion = dbVersion;
    }
    public void setTeachersRegisterHash(String teachersRegisterHash) {
        if (teachersRegisterHash == null || teachersRegisterHash.isBlank())
            throw new IllegalArgumentException("teachersRegisterHash cannot be null or blank");
        this.teachersRegisterHash = teachersRegisterHash;
    }
    public int getDbVersion() {return dbVersion;}
    public String getTeachersRegisterHash() {return teachersRegisterHash;}

    //toString
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

    private static void validate(int dbVersion, String teachersRegisterHash) {
        if (dbVersion < 0)
            throw new IllegalArgumentException("dbVersion cannot be less than 0");

        if (teachersRegisterHash == null || teachersRegisterHash.isBlank())
            throw new IllegalArgumentException("teachersRegisterHash cannot be null or blank");
    }
}
