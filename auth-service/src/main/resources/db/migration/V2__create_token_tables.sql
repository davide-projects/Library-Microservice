CREATE TABLE revoked_tokens (
    jti         VARCHAR(36)     NOT NULL,
    expires_at  TIMESTAMP       NOT NULL,
    revoked_at  TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (jti)
);

CREATE TABLE refresh_tokens (
    jti             VARCHAR(36)     NOT NULL,
    user_id         INT             NOT NULL,
    expires_at      TIMESTAMP       NOT NULL,
    revoked_at      TIMESTAMP       NULL,
    replaced_by_jti VARCHAR(36)     NULL,
    created_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (jti),
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);
