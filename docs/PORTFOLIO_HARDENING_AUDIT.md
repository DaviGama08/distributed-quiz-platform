# Portfolio Hardening Audit

## Executive summary

This repository implements a distributed academic quiz platform with a JavaFX client, a TCP/UDP quiz cluster, a UDP directory service, SQLite persistence, and Maven. The baseline compiles on Windows with JDK 24, but Maven originally reported zero tests. The repository also tracked a copied Windows JavaFX runtime, copied dependency JARs, an SQLite database, and internal academic PDFs. These files are not required for a Maven build and substantially increase publication and portability risk.

The hardening work is intentionally incremental. It preserves the existing wire protocol and persisted schema. It does not claim cryptographic node authentication or cross-platform JavaFX support.

## Technology and executables

- Java 23 bytecode target; JavaFX 20; Maven 3.9.11.
- SQLite via `sqlite-jdbc`; Jackson; Jansi; JUnit Jupiter.
- Entry points: `pt.isec.directory.MainDirectory`, `pt.isec.server.MainServer`, and `pt.isec.client.ClientApplication`.
- Baseline build: `mvn -B clean test` succeeded on 2026-08-01, but executed zero tests.

## Current structure and runtime configuration

- `src/main/java/pt/isec/common`: DTOs, protocol messages, and shared models.
- `src/main/java/pt/isec/directory`: directory-service state and UDP workers.
- `src/main/java/pt/isec/server`: cluster, persistence, authentication, and quiz services.
- `src/main/java/pt/isec/client`: JavaFX application, controllers, and client networking.
- `src/main/resources`: schema, images, and styles.
- `src/test/java`: deterministic unit tests added by this branch.

The server and directory already accept explicit parameters. The baseline client instead embedded `localhost:9999`; this branch resolves the client endpoint from JavaFX named arguments, JVM properties, or environment variables. Windows scripts are being migrated away from copied libraries and private LAN addresses.

## Security and sensitive-data findings

- No current source match was found for common API-key, token, private-key, or permissive-TLS markers.
- Passwords use PBKDF2-HMAC-SHA-256 with a random salt and 210,000 iterations. The implementation was embedded in a database service and had no regression test; it is isolated and tested on this branch.
- The academic node protocol has no cryptographic authentication or message integrity. It is suitable only for a trusted demonstration network until that is designed and tested.
- `guide/identifier.sqlite` is a tracked local database. Its contents are not required for the build and are removed from the public index without deleting the local copy.
- Internal assignment/checklist PDFs are removed from the public index.

## Generated and unnecessary tracked files

- `src/main/java/lib/`: copied JavaFX Windows runtime, DLLs, and dependency JARs (including an approximately 84 MB WebKit DLL).
- `guide/identifier.sqlite`: local SQLite data.
- `docs/PD-2025-26-Enunciado-TP-v2.pdf` and `docs/PD-2025-26-tp-checklist.pdf`: internal academic material.
- Maven `target/` is ignored and was not tracked.

## Dependencies and portability

Maven resolves all runtime libraries after copied binaries are removed. The POM currently fixes JavaFX classifiers to `win`; consequently only Windows packaging is documented and cross-platform support is not claimed. JavaFX 20, Jackson 2.15.2, SQLite JDBC 3.41.2.1, JUnit 5.10.0, and Jansi 2.4.0 remain unchanged to avoid an unrelated dependency migration.

## Architecture, concurrency, and networking risks

- P1: baseline batch scripts depend on copied libraries and contain a private LAN address.
- P1: network code still contains indefinite blocking reads in established TCP sessions. Connect timeouts exist, but full message-level timeout and failover coverage remains incomplete.
- P1: replication transports SQL strings rather than a versioned, authenticated command format.
- P2: mutable cluster state and several long-running worker threads require broader integration tests for failover, reconnection, and shutdown.
- P2: protocol parsing is only partially isolated from sockets.
- P2: fixed Windows JavaFX classifier limits portability.

## README consistency

The baseline README correctly describes the three processes and the fixed Windows JavaFX classifier, but included obsolete client constants and examples tied to the old scripts. It must only be updated after final validation so commands and test counts match observed results.

## External resources and licensing

The repository has no open-source licence. The application icon and institutional logo have no provenance record in this checkout; redistribution rights remain unverified. Dependency licences are supplied by Maven artefacts, but no consolidated third-party notice has yet been produced.

## Risk register

- P0: copied runtime/dependency binaries, local database, and internal PDFs tracked for public distribution.
- P1: non-reproducible scripts with a private IP; client endpoint hard-coded; no baseline tests; unauthenticated academic node protocol.
- P2: Windows-only JavaFX dependency selection; incomplete timeout/failover tests; resource provenance not documented.
- P3: stale comments and duplicated script structure.

## Changes made on `portfolio-hardening`

- Created this audit before functional edits.
- Removed copied binaries, the local SQLite file, and internal PDFs from Git tracking while preserving local copies.
- Added validated client endpoint configuration.
- Isolated PBKDF2 hashing and added deterministic verification tests.
- Replaced copied-library batch flows with Maven-based, parameterised scripts.

## Validation commands and observed results

- Baseline `mvn -B clean test`: success; 0 tests executed.
- Final `mvn -B clean test`: success; 6 tests, 0 failures, 0 errors, 0 skipped.
- Final `mvn -B clean verify`: success; 6 tests and JAR packaging completed.
- Manual multi-process smoke test: pending; requires available ports and interactive JavaFX.

## Remaining limitations and manual actions

- No secret value was found in the current tree, but Git history scanning and credential rotation cannot be inferred from this checkout alone.
- Cryptographic authentication between nodes is not implemented.
- JavaFX packaging is Windows-specific.
- Resource provenance and licences require confirmation from the authors before public redistribution.
- Real network failover, reconnection, replication, and timeout scenarios still need integration coverage.
