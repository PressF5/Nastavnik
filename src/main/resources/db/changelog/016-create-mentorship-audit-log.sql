--liquibase formatted sql
--changeset nastavnik:016-create-mentorship-audit-log
CREATE TABLE mentorship_audit_log (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    mentorship_id  BIGINT NOT NULL,
    msu_id         BIGINT NOT NULL,
    actor_id       BIGINT,
    action         VARCHAR(30) NOT NULL,
    old_status     VARCHAR(20),
    new_status     VARCHAR(20),
    comment        TEXT,
    source         VARCHAR(10) NOT NULL DEFAULT 'USER',
    created_at     TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT     fk_mentorship_audit_log_mentorship_msu FOREIGN KEY (mentorship_id, msu_id) REFERENCES mentorship (id, msu_id) ON DELETE RESTRICT,
    CONSTRAINT     fk_mentorship_audit_log_actor_msu FOREIGN KEY (actor_id, msu_id) REFERENCES users (id, msu_id) ON DELETE RESTRICT,
    CONSTRAINT     chk_mentorship_audit_log_source CHECK (source IN ('USER', 'SYSTEM')),
    CONSTRAINT     chk_mentorship_audit_log_actor_source CHECK ((source = 'USER' AND actor_id IS NOT NULL) OR (source = 'SYSTEM' AND actor_id IS NULL))
);