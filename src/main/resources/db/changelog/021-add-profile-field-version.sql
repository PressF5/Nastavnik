--liquibase formatted sql
--changeset nastavnik:021-add-profile-field-version
ALTER TABLE profile_field ADD COLUMN version BIGINT NOT NULL DEFAULT 0;