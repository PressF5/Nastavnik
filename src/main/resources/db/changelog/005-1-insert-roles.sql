--liquibase formatted sql
--changeset nastavnik:005-1-insert-roles
INSERT INTO role (id, code, name)
VALUES
    (1, 'REGION_ADMIN', 'Администратор региона'),
    (2, 'MSU_ADMIN', 'Администратор МСУ'),
    (3, 'OO_ADMIN', 'Администратор ОО'),
    (4, 'MENTOR', 'Наставник'),
    (5, 'MENTEE', 'Наставляемый');