--liquibase formatted sql
--changeset nastavnik:005-1-insert-roles
INSERT INTO role (code, name)
VALUES
    ('REGION_ADMIN', 'Администратор региона'),
    ('MSU_ADMIN', 'Администратор МСУ'),
    ('OO_ADMIN', 'Администратор ОО'),
    ('MENTOR', 'Наставник'),
    ('MENTEE', 'Наставляемый');