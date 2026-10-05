--changeset nastavnik:018-create-unique-partial-indexes
-- Только один активный учебный год.
CREATE UNIQUE INDEX uk_academic_year_one_active ON academic_year (active) WHERE active = TRUE;

-- У одного наставляемого может быть только одна действующая связь ACCEPTED.
CREATE UNIQUE INDEX uk_active_mentorship_mentee ON mentorship (mentee_id) WHERE status = 'ACCEPTED';

-- Для одной пары в одном учебном году не может одновременно существовать несколько PENDING/ACCEPTED связей.
CREATE UNIQUE INDEX uk_active_mentorship_pair_year ON mentorship (mentor_id, mentee_id, academic_year_id) WHERE status IN ('PENDING', 'ACCEPTED');