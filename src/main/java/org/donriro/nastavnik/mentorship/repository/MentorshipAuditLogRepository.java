package org.donriro.nastavnik.mentorship.repository;

import org.donriro.nastavnik.mentorship.entity.MentorshipAuditLog;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MentorshipAuditLogRepository extends JpaRepository<MentorshipAuditLog, Long> {

    @EntityGraph(attributePaths = "actor")
    List<MentorshipAuditLog> findAllByMentorship_IdOrderByCreatedAtAsc(Long mentorshipId);
}
