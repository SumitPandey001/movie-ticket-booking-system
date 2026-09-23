-- ShedLock's table: one row per scheduled job, so only one instance runs each round
CREATE TABLE shedlock (
    name        VARCHAR(64)   PRIMARY KEY,
    lock_until  TIMESTAMP     NOT NULL,
    locked_at   TIMESTAMP     NOT NULL,
    locked_by   VARCHAR(255)  NOT NULL
);
