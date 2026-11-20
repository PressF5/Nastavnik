package org.donriro.nastavnik.mentorship.repository;

import org.donriro.nastavnik.mentorship.entity.MentorParticipation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface MentorParticipationRepository extends JpaRepository<MentorParticipation, Long> {

    /**
     * Находит подтверждение участия наставника в конкретном учебном году.
     */
    Optional<MentorParticipation> findByMentorIdAndAcademicYearId(Long mentorId, Long academicYearId);

    /**
     * Проверяет, подтвердил ли наставник участие в конкретном учебном году.
     */
    boolean existsByMentor_IdAndAcademicYear_IdAndParticipatedTrue(Long mentorId, Long academicYearId);
}
