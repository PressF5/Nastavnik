--liquibase formatted sql
--changeset nastavnik:020-add-profile-field-matching-enabled
ALTER TABLE profile_field ADD COLUMN matching_enabled BOOLEAN NOT NULL DEFAULT FALSE;