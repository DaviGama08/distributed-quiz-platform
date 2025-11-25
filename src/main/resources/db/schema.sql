PRAGMA foreign_keys = ON;

-- Tabela de configuração: versão da BD e hash do código de registo dos docentes
CREATE TABLE IF NOT EXISTS config (
                                      id                INTEGER PRIMARY KEY CHECK (id = 1),
                                      db_version        INTEGER NOT NULL DEFAULT 0,
                                      teacher_code_hash TEXT    NOT NULL,
                                      created_at        TEXT    DEFAULT CURRENT_TIMESTAMP
);

-- linha única inicial
INSERT OR IGNORE INTO config (id, db_version, teacher_code_hash)
VALUES (
           1,
           0,
           '210000:KdbtTbzp4mwwjPfYW/Prww==:QqDAdfAIIsI4W5Cc+CB7dHJS0m3Nrre3Wa7GGXaWXY4='
       );

-- Docentes
CREATE TABLE IF NOT EXISTS teacher (
                                       id            INTEGER PRIMARY KEY AUTOINCREMENT,
                                       name          TEXT NOT NULL,
                                       email         TEXT NOT NULL UNIQUE,
                                       password_hash TEXT NOT NULL,
                                       created_at    TEXT DEFAULT CURRENT_TIMESTAMP,
                                       updated_at    TEXT DEFAULT CURRENT_TIMESTAMP
);

-- Estudantes
CREATE TABLE IF NOT EXISTS student (
                                       id             INTEGER PRIMARY KEY AUTOINCREMENT,
                                       student_number INTEGER NOT NULL UNIQUE,
                                       name           TEXT NOT NULL,
                                       email          TEXT NOT NULL UNIQUE,
                                       password_hash  TEXT NOT NULL,
                                       created_at     TEXT DEFAULT CURRENT_TIMESTAMP,
                                       updated_at     TEXT DEFAULT CURRENT_TIMESTAMP
);

-- Sessões (ainda podes não usar, mas fica preparada)
CREATE TABLE IF NOT EXISTS session (
                                       id         TEXT PRIMARY KEY,  -- token (UUID)
                                       user_id    TEXT NOT NULL,
                                       role       TEXT NOT NULL CHECK (role IN ('TEACHER','STUDENT')),
                                       name       TEXT NOT NULL,
                                       email      TEXT NOT NULL,
                                       created_at TEXT DEFAULT CURRENT_TIMESTAMP,
                                       expires_at TEXT
);

-- Perguntas
CREATE TABLE IF NOT EXISTS question (
                                        id             INTEGER PRIMARY KEY AUTOINCREMENT,
                                        teacher_id     INTEGER NOT NULL REFERENCES teacher(id) ON DELETE CASCADE,
                                        statement      TEXT NOT NULL,
                                        access_code    TEXT NOT NULL UNIQUE,   -- código para o aluno entrar
                                        correct_option CHAR(1) NOT NULL CHECK (correct_option BETWEEN 'A' AND 'Z'),
                                        start_at       TEXT NOT NULL,          -- ISO-8601 (LocalDateTime.toString())
                                        end_at         TEXT NOT NULL,
                                        created_at     TEXT DEFAULT CURRENT_TIMESTAMP,
                                        CHECK (end_at > start_at)
);

-- Opções das perguntas
CREATE TABLE IF NOT EXISTS option (
                                      id          INTEGER PRIMARY KEY AUTOINCREMENT,
                                      question_id INTEGER NOT NULL REFERENCES question(id) ON DELETE CASCADE,
                                      letter      CHAR(1) NOT NULL CHECK (letter BETWEEN 'A' AND 'Z'),
                                      text        TEXT NOT NULL,
                                      UNIQUE(question_id, letter)
);

-- Respostas dos estudantes
CREATE TABLE IF NOT EXISTS answer (
                                      student_id     INTEGER NOT NULL REFERENCES student(id) ON DELETE CASCADE,
                                      question_id    INTEGER NOT NULL REFERENCES question(id) ON DELETE CASCADE,
                                      chosen_option  CHAR(1) NOT NULL CHECK (chosen_option BETWEEN 'A' AND 'Z'),
                                      created_at     TEXT DEFAULT CURRENT_TIMESTAMP,
                                      PRIMARY KEY (student_id, question_id)
);

-- Índices úteis
CREATE INDEX IF NOT EXISTS idx_teacher_email    ON teacher(email);
CREATE INDEX IF NOT EXISTS idx_student_email    ON student(email);
CREATE INDEX IF NOT EXISTS idx_answer_student   ON answer(student_id);
CREATE INDEX IF NOT EXISTS idx_question_teacher ON question(teacher_id);
