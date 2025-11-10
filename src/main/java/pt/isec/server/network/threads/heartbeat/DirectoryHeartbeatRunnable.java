// FILE: src/main/java/pt/isec/server/network/threads/heartbeat/DirectoryHeartbeatRunnable.java
package pt.isec.server.network.threads.heartbeat;

import pt.isec.server.network.IServerNode;
import pt.isec.server.repositories.DatabaseFiles;
import pt.isec.server.repositories.Db;
import pt.isec.server.services.config.ConfigServices;
import pt.isec.server.network.threads.db.DbCopyRequesterRunnable;

import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Objects;

/**
 * UDP com a diretoria:
 * - envia REGISTER/HEARTBEAT/DEREGISTER
 * - recebe "200 PRINCIPAL ip:port"
 * - PRIMÁRIO: cria BD se faltar e semeia config
 * - BACKUP: se faltar BD, pede cópia ao primário
 */
public class DirectoryHeartbeatRunnable implements Runnable, AutoCloseable {
    private static final int SOCKET_TIMEOUT_MS = 3000;
    private static final int HEARTBEAT_INTERVAL_MS = 5000;
    private static final int RETRY_COUNT = 3;
    private static final int SLEEP_INTERVAL_MS = 50;
    private static final int BUFFER_SIZE = 512;

    private final IServerNode tInfo;
    private DatagramSocket socket;

    public DirectoryHeartbeatRunnable(IServerNode tInfo) { this.tInfo = tInfo; }

    @Override
    public void run() {
        try (DatagramSocket s = new DatagramSocket()) {
            socket = s;
            s.setSoTimeout(SOCKET_TIMEOUT_MS);

            InetAddress dirAddr = InetAddress.getByName(tInfo.directoryHost());
            int dirPort = tInfo.directoryPort();

            // REGISTER
            String registerMsg = kv(
                    "VER","1","TYPE","REGISTER",
                    "ID", tInfo.id(),
                    "TCP", tInfo.ip() + ":" + tInfo.clientPort(),
                    "DBV", String.valueOf(tInfo.dbVersion()),
                    "DBP", String.valueOf(tInfo.dbCopyPort())
            );
            send(s, dirAddr, dirPort, registerMsg);

            // PRINCIPAL
            Endpoint principal = waitPrincipal(s);
            if (principal == null) {
                System.err.println("[DIR] sem resposta da diretoria; thread terminada");
                return;
            }

            tInfo.setPrimary(principal.ip, principal.port);
            boolean iAmPrimary = Objects.equals(principal.ip, tInfo.ip()) && principal.port == tInfo.clientPort();

            if (iAmPrimary) {
                // cria BD se faltar
                DatabaseFiles.createIfMissing(tInfo.dbPath(), "/db/schema.sql");

                // semear config mínima (exemplo)
                Db db = new Db("jdbc:sqlite:" + tInfo.dbPath().toAbsolutePath());
                var cfg = new ConfigServices();
                db.executeUpdate("INSERT OR IGNORE INTO config(key, value) VALUES ('teacher_hash', ?)",
                        cfg.getTeachersRegisterHash());
            } else {
                // backup: se não tem BD local, pede cópia
                if (!Files.exists(tInfo.dbPath())) {
                    new Thread(new DbCopyRequesterRunnable(tInfo, principal.ip, tInfo.dbCopyPort()),
                            "dbcopy-request").start();
                }
            }

            System.out.printf("[DIR] PRINCIPAL %s:%d | iAmPrimary=%s%n", principal.ip, principal.port, iAmPrimary);

            long last = 0;

            // HEARTBEAT loop
            while (tInfo.isRunning()) {
                long now = System.currentTimeMillis();

                if (now - last >= HEARTBEAT_INTERVAL_MS) {
                    String hb = kv("VER","1","TYPE","HEARTBEAT",
                            "ID", tInfo.id(),
                            "DBV", String.valueOf(tInfo.dbVersion()),
                            "DBP", String.valueOf(tInfo.dbCopyPort()));
                    send(s, dirAddr, dirPort, hb);
                    last = now;
                }

                Endpoint update = tryReceivePrincipal(s);
                if (update != null) {
                    tInfo.setPrimary(update.ip, update.port);
                    boolean iAmPrim = Objects.equals(update.ip, tInfo.ip()) && update.port == tInfo.clientPort();
                    System.out.printf("[DIR] PRINCIPAL %s:%d | iAmPrimary=%s%n", update.ip, update.port, iAmPrim);

                    if (!iAmPrim && !Files.exists(tInfo.dbPath())) {
                        new Thread(new DbCopyRequesterRunnable(tInfo, update.ip, tInfo.dbCopyPort()),
                                "dbcopy-request").start();
                    }
                }

                Thread.sleep(SLEEP_INTERVAL_MS);
            }

            // DEREGISTER
            String deregMsg = kv("VER","1","TYPE","DEREGISTER","ID", tInfo.id());
            send(s, dirAddr, dirPort, deregMsg);

        } catch (Exception e) {
            if (tInfo.isRunning())
                System.err.println("[DIR] erro: " + e.getMessage());
        }
    }

    /* ---------- helpers UDP ---------- */

    private record Endpoint(String ip, int port) {}

    private static String kv(String... kv) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < kv.length; i += 2) b.append(kv[i]).append('=').append(kv[i + 1]).append('|');
        return b.toString();
    }

    private void send(DatagramSocket s, InetAddress addr, int port, String msg) throws IOException {
        byte[] data = msg.getBytes(StandardCharsets.UTF_8);
        s.send(new DatagramPacket(data, data.length, addr, port));
    }

    private Endpoint waitPrincipal(DatagramSocket s) {
        for (int i = 0; i < RETRY_COUNT; i++) {
            Endpoint ep = tryReceivePrincipal(s);
            if (ep != null) return ep;
        }
        return null;
    }

    private Endpoint tryReceivePrincipal(DatagramSocket s) {
        try {
            byte[] buf = new byte[BUFFER_SIZE];
            DatagramPacket dp = new DatagramPacket(buf, buf.length);
            s.receive(dp);
            String resp = new String(dp.getData(), 0, dp.getLength(), StandardCharsets.UTF_8).trim();

            if (!resp.startsWith("200 PRINCIPAL ")) return null;
            String[] parts = resp.substring("200 PRINCIPAL ".length()).split(":");
            if (parts.length != 2) return null;

            return new Endpoint(parts[0], Integer.parseInt(parts[1]));
        } catch (SocketTimeoutException e) {
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public void close() {
        if (socket != null && !socket.isClosed()) socket.close();
    }
}
