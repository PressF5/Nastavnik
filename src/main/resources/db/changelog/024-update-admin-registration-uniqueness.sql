--liquibase formatted sql
--changeset nastavnik:024-update-admin-registration-uniqueness

-- Удаляем прежние индексы, которые блокировали регистрацию
-- даже после отклонения предыдущей заявки.

DROP INDEX IF EXISTS uk_users_one_msu_admin;
DROP INDEX IF EXISTS uk_users_one_oo_admin;

-- У одного МСУ может быть только одна заявка администратора
-- со статусом PENDING или ACCEPTED.

CREATE UNIQUE INDEX uk_users_one_msu_admin
    ON users (msu_id)
    WHERE role_id = 2
      AND account_status IN ('PENDING', 'ACCEPTED');

-- У одной образовательной организации может быть только одна
-- заявка администратора со статусом PENDING или ACCEPTED.

CREATE UNIQUE INDEX uk_users_one_oo_admin
    ON users (educational_org_id)
    WHERE role_id = 3
      AND account_status IN ('PENDING', 'ACCEPTED');