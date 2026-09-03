CREATE TABLE revoked_tokens (
    jti VARCHAR(128) PRIMARY KEY,

    user_id BIGINT NOT NULL,

    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,

    revoked_at TIMESTAMP WITH TIME ZONE NOT NULL
        DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT revoked_tokens_user_fk
        FOREIGN KEY (user_id)
        REFERENCES users(id)
);