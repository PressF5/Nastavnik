--liquibase formatted sql
--changeset nastavnik:001-create-citext-extension
CREATE EXTENSION IF NOT EXISTS citext;