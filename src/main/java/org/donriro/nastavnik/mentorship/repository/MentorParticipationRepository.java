package org.donriro.nastavnik.mentorship.repository;

import org.donriro.nastavnik.mentorship.entity.MentorParticipation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.Set;

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

    /**
     * Возвращает ID учебных годов, для которых уже существует запись участия наставника.
     */
    @Query("""
        select participation.academicYear.id
        from MentorParticipation participation
        where participation.mentor.id = :mentorId
          and participation.academicYear.id in :academicYearIds
    """)
    Set<Long> findExistingAcademicYearIds(@Param("mentorId") Long mentorId, @Param("academicYearIds") Set<Long> academicYearIds);

    /**
     * Проверяет, подтвердил ли хотя бы один наставник участие в учебном году.
     */
    boolean existsByAcademicYear_IdAndParticipatedTrue(Long academicYearId);

    /**
     * Удаляет автоматически созданные записи участия для указанного года.
     * Вызывается только после проверки отсутствия подтверждений.
     */
    @Modifying
    @Query("""
    delete from MentorParticipation participation
    where participation.academicYear.id = :academicYearId
    """)
    void deleteAllByAcademicYearId(@Param("academicYearId") Long academicYearId);
}
