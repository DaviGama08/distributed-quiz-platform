# Legacy validation

Validated on 2026-08-23 with JDK 23 and Maven on Windows.

## Automated gate

Command:

```powershell
mvn -B clean verify
```

Result: **PASS** — 47 tests, 0 failures, 0 errors, 0 skipped; JAR packaging completed.

The suite covers:

- TCP authentication boundary, roles, spoofed teacher/student IDs, PING/PONG and clean handler shutdown;
- question creation, editing, editing after answers, ownership-safe deletion, active/future/expired joins and student DTO leakage;
- answer option ownership, duplicate submission, time windows and delayed result disclosure;
- login, persisted session resume, revocation, expiry, current profile data and legacy session migration;
- atomic JDBC mutation/version behavior and rollback;
- SQLite snapshot integrity, WAL-visible committed data, SHA-256 rejection, atomic installation and loopback TCP transfer;
- metadata-only heartbeats, retry decision after packet loss, deterministic freshest-copy election and stale/invalid database rejection;
- Java deserialization allow-list acceptance and rejection.

## Manual multi-process smoke test

This remains a human gate because it needs interactive JavaFX windows and process termination. Run on one trusted LAN/demo machine:

```bat
cd src\main\java\batchFiles
run_all.bat
```

The script starts one Directory, three Servers and two Clients. Verify:

1. Both clients discover the same primary.
2. Register and log in as one teacher and one student.
3. Create an active question, join with its code and submit one answer.
4. Confirm the student cannot see correctness before expiry.
5. Stop the primary process; wait beyond the directory heartbeat interval.
6. Confirm the directory elects a healthy server with the freshest `db_version` and both clients reconnect/resume.
7. Confirm the submitted answer and question remain available.
8. Stop every process and confirm no process remains blocked.

Record the date, ports, observed primary changes and any failure before creating a stable tag.

## Deployment boundary

The legacy node protocol is supported only on a trusted LAN/demo network. Directory and cluster UDP traffic has no cryptographic authentication; do not expose those ports directly to the Internet.
