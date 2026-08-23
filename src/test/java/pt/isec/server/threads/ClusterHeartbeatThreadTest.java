package pt.isec.server.threads;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClusterHeartbeatThreadTest {
    @Test
    void heartbeatContainsOnlyClusterMetadata() {
        String heartbeat = ClusterHeartbeatThread.buildHeartbeat("node-a", 12, 9100, 9200);

        assertTrue(heartbeat.contains("serverId=node-a"));
        assertTrue(heartbeat.contains("role=PRIMARY"));
        assertTrue(heartbeat.contains("dbVersion=12"));
        assertTrue(heartbeat.contains("clientPort=9100"));
        assertTrue(heartbeat.contains("dbCopyPort=9200"));
        assertFalse(heartbeat.toLowerCase().contains("sql"));
    }

    @Test
    void everyLaterHeartbeatCanRecoverAFormerlyLostUpdate() {
        assertTrue(ClusterHeartbeatThread.shouldRequestSnapshot(5, 6, false));
        assertFalse(ClusterHeartbeatThread.shouldRequestSnapshot(6, 6, false));
        assertTrue(ClusterHeartbeatThread.shouldRequestSnapshot(-1, 6, true));
        assertFalse(ClusterHeartbeatThread.shouldRequestSnapshot(7, 6, false));
    }
}
