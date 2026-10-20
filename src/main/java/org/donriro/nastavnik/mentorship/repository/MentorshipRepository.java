package org.donriro.nastavnik.mentorship.repository;

import org.donriro.nastavnik.mentorship.entity.Mentorship;
import org.donriro.nastavnik.mentorship.status.MentorshipStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MentorshipRepository extends JpaRepository<Mentorship, Long> {

    List<Mentorship> findAllByMentor_IdAndStatusOrderByCreatedAtDesc(
            Long mentorId,
            MentorshipStatus status
    );

    boolean existsByMentee_IdAndStatusIn(
            Long menteeId,
            List<MentorshipStatus> statuses
    );

    boolean existsByMentor_IdAndMentee_IdAndAcademicYear_IdAndStatusIn(
            Long mentorId,
            Long menteeId,
            Long academicYearId,
            List<MentorshipStatus> statuses
    );

    boolean existsByIdAndMentor_IdAndStatus(
            Long mentorshipId,
            Long mentorId,
            MentorshipStatus status
    );
}
