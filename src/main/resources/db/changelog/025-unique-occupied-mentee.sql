--liquibase formatted sql
--changeset nastavnik:025-unique-occupied-mentee

-- Заменяем ограничение, которое учитывало только ACCEPTED.
DROP INDEX IF EXISTS uk_active_mentorship_mentee;

-- У наставляемого может быть только одна ожидающая заявка
-- или одно действующее назначение.
CREATE UNIQUE INDEX uk_mentorship_one_occupied_mentee
    ON mentorship (mentee_id)
    WHERE status IN ('PENDING', 'ACCEPTED');
