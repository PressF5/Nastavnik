package org.donriro.nastavnik.mentorship.repository;

import org.donriro.nastavnik.mentorship.entity.Mentorship;
import org.donriro.nastavnik.mentorship.status.MentorshipStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MentorshipRepository extends JpaRepository<Mentorship, Long> {

    // Заявки, адресованные конкретному наставнику.
    List<Mentorship> findAllByMentor_IdAndStatusOrderByCreatedAtDesc(Long mentorId, MentorshipStatus status);

    // Действующие наставляемые конкретного наставника.
    List<Mentorship> findAllByMentor_IdAndStatusOrderByStartedAtDesc(Long mentorId, MentorshipStatus status);

    // Проверяем, есть ли у наставляемого ожидающая или действующая связь.
    boolean existsByMentee_IdAndStatusIn(Long menteeId, List<MentorshipStatus> statuses);

    // Проверяем, есть ли у наставника действующая связь с наставляемым.
    boolean existsByMentor_IdAndMentee_IdAndAcademicYear_IdAndStatus(Long mentorId, Long menteeId, Long academicYearId, MentorshipStatus status);
    long countByMentor_IdAndStatus(Long mentorId, MentorshipStatus status);
    Optional<Mentorship> findByIdAndMentor_Id(Long mentorshipId, Long mentorId);
}