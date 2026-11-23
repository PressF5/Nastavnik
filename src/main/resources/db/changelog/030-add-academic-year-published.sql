--liquibase formatted sql
--changeset nastavnik:030-add-academic-year-published

-- Новые учебные годы по умолчанию создаются неопубликованными.
ALTER TABLE academic_year
    ADD COLUMN published BOOLEAN NOT NULL DEFAULT FALSE;

-- Ранее созданные учебные годы считаются опубликованными,
-- поскольку до появления публикации они были доступны пользователям.
UPDATE academic_year
SET published = TRUE;
