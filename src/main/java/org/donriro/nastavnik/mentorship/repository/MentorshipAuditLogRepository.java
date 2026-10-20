package org.donriro.nastavnik.mentorship.repository;

import org.donriro.nastavnik.mentorship.entity.MentorshipAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MentorshipAuditLogRepository extends JpaRepository<MentorshipAuditLog, Long> {
}
