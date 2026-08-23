package pt.isec.directory.core;

import pt.isec.directory.threads.ServerInfo;

import java.util.Collection;
import java.util.Comparator;

/** Deterministic primary election based on the freshest valid database. */
public final class ServerElection {
    private static final Comparator<ServerInfo> FRESHEST_FIRST =
            Comparator.comparingLong(ServerInfo::getDbVersion).reversed()
                    .thenComparingLong(ServerInfo::getRegisteredAtMillis)
                    .thenComparing(ServerInfo::getId);

    private ServerElection() {
    }

    public static ServerInfo selectFreshest(Collection<ServerInfo> servers) {
        return servers.stream()
                .filter(server -> server != null && server.getDbVersion() >= 0)
                .min(FRESHEST_FIRST)
                .orElse(null);
    }
}
