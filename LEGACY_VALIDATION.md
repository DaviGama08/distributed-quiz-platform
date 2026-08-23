# Legacy validation

Validated on 2026-08-23 with JDK 23 and Maven on Windows.

## Automated gate

Command:

```powershell
mvn -B clean verify
```

Result: **PASS** — 48 tests, 0 failures, 0 errors, 0 skipped; JAR packaging completed.

The suite covers:

- TCP authentication boundary, roles, spoofed teacher/student IDs, PING/PONG and clean handler shutdown;
- question creation, editing, editing after answers, ownership-safe deletion, active/future/expired joins and student DTO leakage;
- answer option ownership, duplicate submission, time windows and delayed result disclosure;
- login, persisted session resume, revocation, expiry, current profile data and legacy session migration;
- atomic JDBC mutation/version behavior and rollback;
- SQLite snapshot integrity, WAL-visible committed data, SHA-256 rejection, atomic installation and loopback TCP transfer;
- metadata-only heartbeats, retry decision after packet loss, deterministic freshest-copy election and stale/invalid database rejection;
- Java deserialization allow-list acceptance and rejection;
- configured directory TTL expiry and deterministic, non-blocking shutdown.

## Multi-process and JavaFX smoke gate

Command:

```powershell
mvn -B -Dtest=LegacyMultiProcessSmoke test
```

Result on 2026-08-23: **PASS** — one Directory process, three independent
Server JVMs, two concurrent functional client connections and two real JavaFX
client JVMs completed without failures.

Final observed run: Directory UDP `60827`; server TCP ports `53760`, `53756`
and `53758`; primary changed from `192.168.1.189:53760` to
`192.168.1.189:53756`; all copies converged at `db_version=8`. Ports are
dynamically reserved on every run.

The repeatable gate:

1. reserves isolated dynamic UDP/TCP ports and data folders;
2. confirms both clients discover the same primary;
3. registers and logs in one teacher and one student;
4. creates an active question, joins it and submits an answer;
5. confirms correctness is hidden before expiry;
6. waits for all three databases to converge to the same `db_version`;
7. opens and cleanly closes two JavaFX client windows;
8. forcibly terminates the primary and observes a different fresh primary;
9. resumes both persisted sessions and verifies the question and answer;
10. gracefully stops both remaining servers and the Directory without blocked processes.

The batch-file equivalent for a human demonstration remains:

```bat
cd src\main\java\batchFiles
run_all.bat
```

## Deployment boundary

The legacy node protocol is supported only on a trusted LAN/demo network. Directory and cluster UDP traffic has no cryptographic authentication; do not expose those ports directly to the Internet.
