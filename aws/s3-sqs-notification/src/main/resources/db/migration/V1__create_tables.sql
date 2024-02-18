CREATE TABLE file
(
    id         BIGSERIAL PRIMARY KEY,
    version    INT                      NOT NULL DEFAULT 0,
    path       VARCHAR(64)              NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    contents   TEXT
);
