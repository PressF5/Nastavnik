package org.donriro.nastavnik.mentorship.repository;

import org.donriro.nastavnik.mentorship.entity.Mentorship;
import org.donriro.nastavnik.mentorship.status.MentorshipStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    /**
     * Назначения наставничества наставляемых конкретной ОО.
     * Поиск выполняется по ФИО наставника и наставляемого.
     * Статус необязательный: если не передан, возвращаются все назначения.
     */
    @Query(
            value = """
                SELECT m
                FROM Mentorship m
                JOIN m.mentee mentee
                JOIN m.mentor mentor
                WHERE mentee.educationalOrganization.id = :organizationId
                  AND (:status IS NULL OR m.status = :status)
                  AND (
                      :search = ''
                      OR LOWER(
                          CONCAT(
                              CONCAT(
                                  CONCAT(COALESCE(mentee.lastName, ''), ' '),
                                  COALESCE(mentee.firstName, '')
                              ),
                              CONCAT(' ', COALESCE(mentee.middleName, ''))
                          )
                      ) LIKE LOWER(CONCAT('%', :search, '%'))
                      OR LOWER(
                          CONCAT(
                              CONCAT(
                                  CONCAT(COALESCE(mentor.lastName, ''), ' '),
                                  COALESCE(mentor.firstName, '')
                              ),
                              CONCAT(' ', COALESCE(mentor.middleName, ''))
                          )
                      ) LIKE LOWER(CONCAT('%', :search, '%'))
                  )
                """,
            countQuery = """
                SELECT COUNT(m)
                FROM Mentorship m
                JOIN m.mentee mentee
                JOIN m.mentor mentor
                WHERE mentee.educationalOrganization.id = :organizationId
                  AND (:status IS NULL OR m.status = :status)
                  AND (
                      :search = ''
                      OR LOWER(
                          CONCAT(
                              CONCAT(
                                  CONCAT(COALESCE(mentee.lastName, ''), ' '),
                                  COALESCE(mentee.firstName, '')
                              ),
                              CONCAT(' ', COALESCE(mentee.middleName, ''))
                          )
                      ) LIKE LOWER(CONCAT('%', :search, '%'))
                      OR LOWER(
                          CONCAT(
                              CONCAT(
                                  CONCAT(COALESCE(mentor.lastName, ''), ' '),
                                  COALESCE(mentor.firstName, '')
                              ),
                              CONCAT(' ', COALESCE(mentor.middleName, ''))
                          )
                      ) LIKE LOWER(CONCAT('%', :search, '%'))
                  )
                """
    )
    Page<Mentorship> findAllForOrganization(
            @Param("organizationId") Long organizationId,
            @Param("status") MentorshipStatus status,
            @Param("search") String search,
            Pageable pageable);
}