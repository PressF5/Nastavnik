--changeset nastavnik:018-create-unique-partial-indexes
-- Только один активный учебный год.
CREATE UNIQUE INDEX uk_academic_year_one_active ON academic_year (active) WHERE active = TRUE;

-- У одного наставляемого может быть только одна действующая связь ACCEPTED.
CREATE UNIQUE INDEX uk_active_mentorship_mentee ON mentorship (mentee_id) WHERE status = 'ACCEPTED';

-- Для одной пары в одном учебном году не может одновременно существовать несколько PENDING/ACCEPTED связей.
CREATE UNIQUE INDEX uk_active_mentorship_pair_year ON mentorship (mentor_id, mentee_id, academic_year_id) WHERE status IN ('PENDING', 'ACCEPTED');

-- Только один REGION_ADMIN во всей системе.
CREATE UNIQUE INDEX uk_users_one_region_admin ON users (role_id) WHERE role_id = 1;

-- Только один MSU_ADMIN для каждого МСУ.
CREATE UNIQUE INDEX uk_users_one_msu_admin ON users (role_id, msu_id) WHERE role_id = 2;

-- Только один OO_ADMIN для каждой образовательной организации.
CREATE UNIQUE INDEX uk_users_one_oo_admin ON users (role_id, educational_org_id) WHERE role_id = 3;