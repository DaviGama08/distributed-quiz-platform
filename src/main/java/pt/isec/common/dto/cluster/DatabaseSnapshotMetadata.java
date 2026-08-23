package pt.isec.common.dto.cluster;

import java.io.Serializable;

/** Metadata authenticated by the receiver before installing a database snapshot. */
public record DatabaseSnapshotMetadata(long size, byte[] sha256, long dbVersion)
        implements Serializable {

    public DatabaseSnapshotMetadata {
        if (size < 0) {
            throw new IllegalArgumentException("Snapshot size cannot be negative");
        }
        if (sha256 == null || sha256.length != 32) {
            throw new IllegalArgumentException("SHA-256 must contain 32 bytes");
        }
        if (dbVersion < 0) {
            throw new IllegalArgumentException("Database version cannot be negative");
        }
        sha256 = sha256.clone();
    }

    @Override
    public byte[] sha256() {
        return sha256.clone();
    }
}
