--liquibase formatted sql
--changeset nastavnik:012-create-profile-value-option
CREATE TABLE profile_value_option (
    profile_value_id   BIGINT NOT NULL,
    profile_option_id  BIGINT NOT NULL,
    profile_field_id   BIGINT NOT NULL,

    CONSTRAINT         pk_profile_value_option PRIMARY KEY (profile_value_id, profile_option_id),
    CONSTRAINT         fk_profile_value_option_value_field FOREIGN KEY (profile_value_id, profile_field_id) REFERENCES profile_value (id, profile_field_id) ON DELETE CASCADE,
    CONSTRAINT         fk_profile_value_option_option_field FOREIGN KEY (profile_option_id, profile_field_id) REFERENCES profile_field_option (id, profile_field_id)
);