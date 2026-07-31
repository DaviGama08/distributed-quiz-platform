 <div align="center">

# Distributed Quiz Platform

### Fault-tolerant question-and-answer system built with Java and distributed systems concepts

![Java](https://img.shields.io/badge/Java-23-ED8B00?style=flat-square&logo=openjdk&logoColor=white)
![JavaFX](https://img.shields.io/badge/JavaFX-20-1F9CF0?style=flat-square)
![SQLite](https://img.shields.io/badge/SQLite-3-003B57?style=flat-square&logo=sqlite&logoColor=white)
![Maven](https://img.shields.io/badge/Maven-Build-C71A36?style=flat-square&logo=apachemaven&logoColor=white)
![Status](https://img.shields.io/badge/Status-Completed-success?style=flat-square)

</div>

## Overview

**Distributed Quiz Platform** is a fault-tolerant client-server system for creating and answering multiple-choice questions in a classroom environment.

The platform supports teachers and students through a JavaFX desktop application. Behind the interface, a cluster of servers provides service discovery, local database replication, asynchronous notifications and recovery from server failures.

Only one server handles client requests at a given time. The remaining servers operate as backups, maintaining synchronised SQLite databases and becoming eligible to replace the primary server when it becomes unavailable.

The project was designed to explore practical distributed-systems concepts such as:

- Service discovery;
- Primary-backup replication;
- Heartbeats and failure detection;
- TCP, UDP and multicast communication;
- Automatic client reconnection;
- Concurrent request processing;
- Asynchronous notifications;
- Local database consistency.

## Main Features

### Teacher

Teachers can:

- Create and update their profile;
- Register using a protected teacher registration code;
- Authenticate using email and password;
- Create multiple-choice questions;
- Define question options and the correct answer;
- Configure question availability periods;
- Generate access codes automatically;
- View active, upcoming and expired questions;
- Filter questions by their current state;
- Edit or delete questions without submitted answers;
- View student answers;
- Analyse answer statistics;
- Export question results to CSV;
- Receive asynchronous updates;
- End their session securely.

### Student

Students can:

- Create and update their profile;
- Authenticate using email and password;
- Join an active question using an access code;
- Submit one answer per question;
- View previously answered questions;
- Check whether an answer was correct after the question expires;
- Receive asynchronous question updates;
- Recover their session after reconnecting to another server;
- End their session securely.

## Distributed Architecture

The system is divided into three main components:

| Component | Responsibility |
| --- | --- |
| **Client** | JavaFX application used by teachers and students |
| **Directory Service** | Tracks active servers and returns the current primary server |
| **Quiz Server** | Processes requests, manages business logic and persists data in SQLite |

```mermaid
flowchart LR
    C1[Teacher Client]
    C2[Student Client]
    D[Directory Service]
    P[Primary Quiz Server]
    B1[Backup Server]
    B2[Backup Server]
    DB1[(SQLite)]
    DB2[(SQLite)]
    DB3[(SQLite)]

    C1 -- UDP discovery --> D
    C2 -- UDP discovery --> D

    D -- Primary endpoint --> C1
    D -- Primary endpoint --> C2

    C1 -- TCP requests and notifications --> P
    C2 -- TCP requests and notifications --> P

    P -- Read and write --> DB1
    B1 -- Local replica --> DB2
    B2 -- Local replica --> DB3

    P -- TCP initial database copy --> B1
    P -- TCP initial database copy --> B2

    P -- Multicast SQL updates and versions --> B1
    P -- Multicast SQL updates and versions --> B2

    P -- UDP heartbeat --> D
    B1 -- UDP heartbeat --> D
    B2 -- UDP heartbeat --> D
```

A more detailed interaction diagram is available in [`docs/Geral.png`](docs/Geral.png).

## How the Distributed System Works

### 1. Server discovery

The directory service starts before the other components and listens for UDP messages.

Every quiz server registers with the directory service. The server that has been registered for the longest period is considered the current primary server.

Clients contact the directory service to obtain the address and TCP port of that primary server.

### 2. Primary and backup servers

The primary server:

- Accepts client connections;
- Processes authentication and business operations;
- Updates its local SQLite database;
- Propagates database changes to backup servers;
- Sends asynchronous notifications to connected clients.

Backup servers:

- Maintain their own SQLite database;
- Receive an initial full database copy through TCP;
- Receive subsequent updates through multicast;
- Track database version numbers;
- Can replace the primary server after a failure.

### 3. Heartbeats and failure detection

Servers periodically send heartbeat messages to:

- The directory service;
- The multicast server group.

Heartbeats carry information such as:

- Server identifier;
- Client TCP port;
- Database-copy TCP port;
- Current database version;
- Database update query, when applicable.

The directory service removes servers that stop sending heartbeats within the configured timeout.

### 4. Database replication

When a backup server starts, it requests a complete copy of the primary server's SQLite database through TCP.

After the initial synchronisation, database changes are propagated incrementally. Each change includes the new database version and the SQL operation that must be applied by the backup servers.

Version validation is used to detect inconsistencies between replicas.

### 5. Automatic client recovery

When the connection to the current server is lost, the client:

1. Contacts the directory service again;
2. Requests the current primary server;
3. Connects to the new server;
4. Restores the previous authenticated session when possible;
5. Updates the JavaFX interface with the new connection status.

This recovery process is designed to minimise disruption to the user.

## Communication Protocols

| Communication | Protocol | Purpose |
| --- | --- | --- |
| Client → Directory | UDP | Primary-server discovery |
| Server → Directory | UDP | Registration, heartbeat and deregistration |
| Client ↔ Primary Server | TCP | Authentication, questions, answers and notifications |
| Backup → Primary Server | TCP | Initial SQLite database copy |
| Server ↔ Server | UDP Multicast | Heartbeats and incremental database replication |

## Security

The authentication layer includes:

- Password hashing using **PBKDF2 with HMAC-SHA-256**;
- Salted password representations;
- Unique email constraints;
- Unique student-number constraints;
- Authentication-required operations;
- Role-based separation between teachers and students;
- Session tracking;
- Server-side validation of permissions.

No plaintext password is stored in the SQLite databases.

## Technology Stack

| Area | Technology |
| --- | --- |
| Programming language | Java 23 |
| Desktop interface | JavaFX 20 |
| Build system | Apache Maven |
| Persistence | SQLite |
| Database access | JDBC |
| Object serialisation | Java serialisation |
| Network communication | TCP, UDP and multicast |
| Concurrency | Java threads and concurrent collections |
| Console output | Jansi |
| Testing support | JUnit 5 |

## Project Structure

```text
distributed-quiz-platform/
├── src/
│   └── main/
│       ├── java/
│       │   └── pt/isec/
│       │       ├── client/
│       │       │   ├── core/
│       │       │   ├── services/
│       │       │   ├── threads/
│       │       │   └── ui/
│       │       ├── server/
│       │       │   ├── core/
│       │       │   ├── db/
│       │       │   ├── services/
│       │       │   └── threads/
│       │       ├── directory/
│       │       │   ├── core/
│       │       │   └── threads/
│       │       └── common/
│       │           ├── dto/
│       │           ├── messages/
│       │           ├── model/
│       │           └── util/
│       └── resources/
│           ├── db/
│           ├── imgs/
│           └── styles/
├── docs/
├── diagrams/
├── guide/
└── pom.xml
```

## Main Packages

### `client`

Contains the JavaFX application, controllers, views, client-side services and communication threads.

The client interface and communication logic are kept separate so that UI operations do not directly manage network connections.

### `server`

Contains the main business logic, TCP connection management, database access, authentication, question management, answer processing and replication mechanisms.

### `directory`

Contains the service-discovery component responsible for tracking active servers and identifying the current primary server.

### `common`

Contains shared objects used by every component:

- Domain models;
- Data-transfer objects;
- TCP and UDP messages;
- Message types;
- Utility classes.

## Database Model

Each quiz server maintains an independent SQLite database containing:

| Table | Purpose |
| --- | --- |
| `config` | Database version and teacher-code hash |
| `teacher` | Teacher profiles and credentials |
| `student` | Student profiles and credentials |
| `session` | Authentication and session information |
| `question` | Question statements, access codes and availability |
| `option` | Multiple-choice options |
| `answer` | Student answers |

Foreign keys and uniqueness constraints are used to preserve data integrity.

## Requirements

The current version is configured primarily for Windows.

You will need:

- Windows 10 or Windows 11;
- JDK 23;
- Apache Maven;
- A network interface that supports multicast;
- UDP and TCP ports available for the directory and quiz servers.

## Configuration

### Directory service

The default directory port is:

```text
9999/UDP
```

The directory service can also receive custom arguments:

```text
MainDirectory <udpPort>
```

or:

```text
MainDirectory <udpPort> <queueCapacity> <maxPacketSize> <ttlMillis>
```

### Quiz server

Each server receives:

```text
MainServer <directoryHost> <directoryPort> <dataDirectory> <multicastInterface> <clientPort> <databaseCopyPort>
```

Example:

```text
MainServer localhost 9999 PROJECT AUTO 5010 17010
```

Where:

- `localhost` is the directory-service host;
- `9999` is the directory UDP port;
- `PROJECT` stores databases inside the project data directory;
- `AUTO` selects a multicast-compatible interface;
- `5010` accepts client TCP connections;
- `17010` accepts database-copy connections.

Every additional server must use different TCP ports.

### Client

The current JavaFX client uses:

```java
private static final String DIRECTORY_IP = "localhost";
private static final int DIRECTORY_PORT = 9999;
```

These values are located in:

```text
src/main/java/pt/isec/client/ClientApplication.java
```

Change them when the directory service is running on another machine.

## Build

Compile the project with Maven:

```bash
mvn clean compile
```

Create the project package:

```bash
mvn clean package
```

## Running on Windows

The repository includes Windows batch scripts under:

```text
src/main/java/batchFiles/
```

Before starting, review the multicast interface configured in the server scripts. Replace any local IP address with:

```text
AUTO
```

or with the IPv4 address of your multicast-compatible network interface.

### Minimal environment

Starts one directory service, one server and one client:

```bat
cd src\main\java\batchFiles
run_all_test.bat
```

### Replicated environment

Starts one directory service, three servers and two clients:

```bat
cd src\main\java\batchFiles
run_all.bat
```

### Start components individually

Start them in this order:

```bat
run_directory.bat
run_server.bat
run_client.bat
```

Additional backup servers can be started with:

```bat
run_server2.bat
run_server3.bat
```

## Example Workflow

### Teacher workflow

1. Register a teacher account;
2. Authenticate;
3. Create a multiple-choice question;
4. Configure its availability period;
5. Share the generated access code;
6. Receive answer notifications;
7. View results and statistics;
8. Export the results to CSV.

### Student workflow

1. Register a student account;
2. Authenticate;
3. Enter the question access code;
4. Select and submit an answer;
5. Wait for the question to expire;
6. View the answer history and result.

## Documentation

The repository includes additional architecture and interaction diagrams:

- [`docs/Geral.png`](docs/Geral.png) — complete system interaction;
- [`docs/Directory.png`](docs/Directory.png) — directory-service flow;
- [`docs/Server.png`](docs/Server.png) — server behaviour;
- [`docs/Client.png`](docs/Client.png) — client behaviour;
- [`docs/Comunicação_geral.png`](docs/Comunicação_geral.png) — communication overview.

## Academic Context

This project was developed as a collaborative assignment for the **Distributed Programming** course of the Bachelor's Degree in Computer Engineering at the **Instituto Superior de Engenharia de Coimbra — ISEC**, during the 2025/2026 academic year.

The project focused on applying distributed-systems concepts through a complete implementation rather than relying on external distributed-system frameworks.

## Current Scope

The implemented version uses Java sockets and object-based message exchange.

REST and RMI interfaces were considered as part of the architectural planning, but they are not implemented in the current version.

---

<div align="center">

Developed as a distributed-systems engineering project.

</div>
