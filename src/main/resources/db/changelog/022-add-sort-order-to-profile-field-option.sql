--liquibase formatted sql
--changeset nastavnik:022-add-sort-order-to-profile-field-option
ALTER TABLE profile_field_option ADD COLUMN sort_order INTEGER NOT NULL DEFAULT 0;