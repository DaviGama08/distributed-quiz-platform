
CREATE TABLE IF NOT EXISTS config (
    id               INTEGER PRIMARY KEY CHECK (id = 1),
    db_version       INTEGER NOT NULL,
    teacher_code_hash TEXT   NOT NULL
);

CREATE TABLE IF NOT EXISTS teacher (
    id             INTEGER PRIMARY KEY AUTOINCREMENT,
    name           TEXT    NOT NULL,
    email          TEXT    NOT NULL UNIQUE,
    password_hash  TEXT    NOT NULL,
    created_at     TEXT    NOT NULL DEFAULT (datetime('now')),
    updated_at     TEXT    NOT NULL DEFAULT (datetime('now'))
);

CREATE TABLE IF NOT EXISTS student (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    student_number  INTEGER NOT NULL UNIQUE,
    name            TEXT    NOT NULL,
    email           TEXT    NOT NULL UNIQUE,
    password_hash   TEXT    NOT NULL,
    created_at      TEXT    NOT NULL DEFAULT (datetime('now')),
    updated_at      TEXT    NOT NULL DEFAULT (datetime('now'))
);


CREATE TABLE IF NOT EXISTS question (
    id               INTEGER PRIMARY KEY AUTOINCREMENT,
    teacher_id       INTEGER NOT NULL,
    statement        TEXT    NOT NULL,
    start_at         TEXT    NOT NULL,
    end_at           TEXT    NOT NULL,
    access_code      TEXT    NOT NULL UNIQUE,
    correct_option_id INTEGER,
    updated_at       TEXT    NOT NULL DEFAULT (datetime('now')),

    FOREIGN KEY (teacher_id)        REFERENCES teacher(id)        ON DELETE CASCADE,
    FOREIGN KEY (correct_option_id) REFERENCES question_option(id) ON DELETE SET NULL
);


CREATE TABLE IF NOT EXISTS question_option (
    id           INTEGER PRIMARY KEY AUTOINCREMENT,
    question_id  INTEGER NOT NULL,
    label        TEXT    NOT NULL,
    text         TEXT    NOT NULL,
    FOREIGN KEY (question_id) REFERENCES question(id) ON DELETE CASCADE,
    UNIQUE (question_id, label)
);


CREATE TABLE IF NOT EXISTS participation (
    id                 INTEGER PRIMARY KEY AUTOINCREMENT,
    student_id         INTEGER NOT NULL,
    question_id        INTEGER NOT NULL,
    selected_option_id INTEGER NOT NULL,
    answer_at          TEXT    NOT NULL DEFAULT (datetime('now')),

    FOREIGN KEY (student_id)         REFERENCES student(id)         ON DELETE CASCADE,
    FOREIGN KEY (question_id)        REFERENCES question(id)        ON DELETE CASCADE,
    FOREIGN KEY (selected_option_id) REFERENCES question_option(id) ON DELETE CASCADE,
    UNIQUE (student_id, question_id)
 );

CREATE INDEX IF NOT EXISTS idx_question_teacher      ON question(teacher_id);
CREATE INDEX IF NOT EXISTS idx_question_window       ON question(start_at, end_at);
CREATE INDEX IF NOT EXISTS idx_participation_q       ON participation(question_id);
CREATE INDEX IF NOT EXISTS idx_participation_student ON participation(student_id);