CREATE TABLE setup_state (
    id SMALLINT PRIMARY KEY,
    setup_locked BOOLEAN NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT chk_setup_state_singleton CHECK (id = 1)
);

INSERT INTO setup_state (id, setup_locked, updated_at) VALUES (1, FALSE, NOW());

CREATE TABLE setup_tokens (
    id UUID PRIMARY KEY,
    token_hash VARCHAR(128) NOT NULL UNIQUE,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    consumed_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_setup_tokens_expires_at ON setup_tokens(expires_at);
CREATE INDEX idx_setup_tokens_consumed_at ON setup_tokens(consumed_at);
