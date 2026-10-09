--liquibase formatted sql
--changeset nastavnik:019-create-indexes
CREATE INDEX idx_profile_value_field_id ON profile_value (profile_field_id);
CREATE INDEX idx_mentor_participation_year ON mentor_participation (academic_year_id);
CREATE INDEX idx_mentorship_mentor ON mentorship (mentor_id);
CREATE INDEX idx_mentorship_mentee ON mentorship (mentee_id);
CREATE INDEX idx_user_audit_log_user_created ON user_audit_log (user_id, created_at DESC);
CREATE INDEX idx_user_audit_log_actor_created ON user_audit_log (actor_id, created_at DESC);
CREATE INDEX idx_mentorship_audit_log_mentorship_created ON mentorship_audit_log (mentorship_id, created_at DESC);
CREATE INDEX idx_mentorship_audit_log_msu_created ON mentorship_audit_log (msu_id, created_at DESC);
CREATE INDEX idx_refresh_token_user_id ON refresh_token (user_id);