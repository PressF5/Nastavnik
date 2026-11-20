--liquibase formatted sql
--changeset nastavnik:028-update-academic-year-period

-- Удаляем ограничение, которое больше не требуется:
-- активность учебного года теперь определяется по датам.
DROP INDEX IF EXISTS uk_academic_year_one_active;

ALTER TABLE academic_year DROP COLUMN active;

-- Запрещаем пересечение периодов учебных годов.
-- Обе даты включаются в период.
ALTER TABLE academic_year ADD CONSTRAINT ex_academic_year_no_overlap
    EXCLUDE USING gist ((daterange(start_date, end_date, '[]')) WITH &&);
