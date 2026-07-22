CREATE TABLE templates (
    id              VARCHAR(64) PRIMARY KEY,
    channel         VARCHAR(32)  NOT NULL,
    name            VARCHAR(128) NOT NULL,
    subject_pattern TEXT         NOT NULL,
    body_pattern    TEXT         NOT NULL,
    required_vars   TEXT         NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE TABLE notifications (
    id              VARCHAR(64) PRIMARY KEY,
    channel         VARCHAR(32)  NOT NULL,
    recipient       VARCHAR(512) NOT NULL,
    template_id     VARCHAR(64)  NOT NULL REFERENCES templates (id),
    variables_json  TEXT         NOT NULL,
    priority        VARCHAR(32)  NOT NULL,
    status          VARCHAR(32)  NOT NULL,
    subject         TEXT         NOT NULL,
    rendered_body   TEXT         NOT NULL,
    attempt_count   INT          NOT NULL DEFAULT 0,
    last_error      TEXT,
    created_at      TIMESTAMPTZ  NOT NULL,
    scheduled_at    TIMESTAMPTZ,
    last_attempt_at TIMESTAMPTZ,
    delivered_at    TIMESTAMPTZ,
    next_retry_at   TIMESTAMPTZ
);

CREATE INDEX idx_notifications_status_scheduled
    ON notifications (status, scheduled_at)
    WHERE status = 'SCHEDULED';

CREATE INDEX idx_notifications_status_retry
    ON notifications (status, next_retry_at)
    WHERE status = 'RETRYING';

CREATE INDEX idx_notifications_created_at ON notifications (created_at DESC);

CREATE TABLE delivery_attempts (
    id                   BIGSERIAL PRIMARY KEY,
    notification_id      VARCHAR(64) NOT NULL REFERENCES notifications (id),
    attempt_number       INT         NOT NULL,
    attempted_at         TIMESTAMPTZ NOT NULL,
    success              BOOLEAN     NOT NULL,
    provider_response    TEXT,
    error_message        TEXT
);

CREATE INDEX idx_delivery_attempts_notification
    ON delivery_attempts (notification_id);

CREATE TABLE dead_letters (
    id              BIGSERIAL PRIMARY KEY,
    notification_id VARCHAR(64) NOT NULL REFERENCES notifications (id),
    reason          TEXT        NOT NULL,
    total_attempts  INT         NOT NULL,
    dead_lettered_at TIMESTAMPTZ NOT NULL,
    payload_json    TEXT        NOT NULL
);

CREATE INDEX idx_dead_letters_notification ON dead_letters (notification_id);

CREATE TABLE provider_inbox (
    id              BIGSERIAL PRIMARY KEY,
    channel         VARCHAR(32)  NOT NULL,
    recipient       VARCHAR(512) NOT NULL,
    subject         TEXT,
    body            TEXT         NOT NULL,
    notification_id VARCHAR(64),
    received_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_provider_inbox_channel ON provider_inbox (channel, received_at DESC);
