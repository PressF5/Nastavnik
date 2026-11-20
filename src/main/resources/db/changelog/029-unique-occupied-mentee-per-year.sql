--liquibase formatted sql
--changeset nastavnik:029-unique-occupied-mentee-per-year

-- Удаляем глобальное ограничение:
-- оно не позволяло наставляемому иметь пары в разных учебных годах.
DROP INDEX IF EXISTS uk_mentorship_one_occupied_mentee;

-- В одном учебном году у наставляемого может быть
-- только одна ожидающая заявка или действующая пара.
CREATE UNIQUE INDEX uk_mentorship_one_occupied_mentee_year
    ON mentorship (mentee_id, academic_year_id)
    WHERE status IN ('PENDING', 'ACCEPTED');