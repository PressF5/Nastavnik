--liquibase formatted sql
--changeset nastavnik:026-limit-profile-matching-rule-weight

ALTER TABLE profile_matching_rule
    DROP CONSTRAINT chk_profile_matching_rule_weight;

ALTER TABLE profile_matching_rule
    ADD CONSTRAINT chk_profile_matching_rule_weight
    CHECK (weight >= 1 AND weight <= 10);