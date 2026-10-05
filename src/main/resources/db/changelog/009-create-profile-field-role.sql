--liquibase formatted sql
--changeset nastavnik:009-create-profile-field-role
CREATE TABLE profile_field_role (
    profile_field_id  BIGINT NOT NULL,
    role_id           SMALLINT NOT NULL,
    required          BOOLEAN NOT NULL DEFAULT FALSE,
    sort_order        INTEGER NOT NULL DEFAULT 0,

    CONSTRAINT        pk_profile_field_role PRIMARY KEY (profile_field_id, role_id),
    CONSTRAINT        fk_profile_field_role_field FOREIGN KEY (profile_field_id) REFERENCES profile_field (id) ON DELETE CASCADE,
    CONSTRAINT        fk_profile_field_role_role FOREIGN KEY (role_id) REFERENCES role (id),
    CONSTRAINT        chk_profile_field_role_sort_order CHECK (sort_order >= 0)
);