--liquibase formatted sql
--changeset nastavnik:027-integer-profile-matching-rule-weight

ALTER TABLE profile_matching_rule
    DROP CONSTRAINT chk_profile_matching_rule_weight;

ALTER TABLE profile_matching_rule
    ADD CONSTRAINT chk_profile_matching_rule_weight
    CHECK (
        weight >= 1
        AND weight <= 10
        AND weight = TRUNC(weight)
    );