package pt.isec.directory.core;

import org.junit.jupiter.api.Test;
import pt.isec.directory.threads.ServerInfo;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ServerElectionTest {
    @Test
    void electsHighestValidDatabaseVersion() {
        ServerInfo old = server("old", 4, 100);
        ServerInfo fresh = server("fresh", 8, 200);
        ServerInfo invalid = server("invalid", -1, 50);

        assertEquals("fresh", ServerElection.selectFreshest(List.of(old, fresh, invalid)).getId());
    }

    @Test
    void stableTieBreakKeepsFirstRegisteredServer() {
        ServerInfo first = server("first", 8, 100);
        ServerInfo second = server("second", 8, 200);

        assertEquals("first", ServerElection.selectFreshest(List.of(second, first)).getId());
    }

    @Test
    void neverPromotesUnknownOrInvalidDatabase() {
        assertNull(ServerElection.selectFreshest(List.of(server("bad", -1, 100))));
    }

    private static ServerInfo server(String id, long version, long registeredAt) {
        return new ServerInfo(id, "127.0.0.1", 9000, 9001, version, registeredAt);
    }
}
