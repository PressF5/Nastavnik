--liquibase formatted sql
--changeset nastavnik:007-create-user-audit-log
CREATE TABLE user_audit_log (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id             BIGINT NOT NULL,
    actor_id            BIGINT,
    action              VARCHAR(40) NOT NULL,
    old_account_status  VARCHAR(20),
    new_account_status  VARCHAR(20),
    old_is_blocked      BOOLEAN,
    new_is_blocked      BOOLEAN,
    comment             TEXT,
    source              VARCHAR(10) NOT NULL DEFAULT 'USER',
    created_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT          fk_user_audit_log_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT          fk_user_audit_log_actor FOREIGN KEY (actor_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT          chk_user_audit_log_source CHECK (source IN ('USER', 'SYSTEM')),
    CONSTRAINT          chk_user_audit_log_actor_source CHECK ((source = 'USER' AND actor_id IS NOT NULL) OR (source = 'SYSTEM' AND actor_id IS NULL))
);