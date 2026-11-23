package org.donriro.nastavnik.mentorship.repository;

import org.donriro.nastavnik.mentorship.dto.MentorParticipationAdminResponse;
import org.donriro.nastavnik.mentorship.entity.MentorParticipation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    /**
     * Создаёт записи участия для всех действующих наставников,
     * у которых ещё нет записи в указанном учебном году.
     * Вставка выполняется непосредственно в PostgreSQL.
     */
    @Modifying
    @Query(value = """
    INSERT INTO mentor_participation (
        mentor_id,
        academic_year_id,
        participated
    )
    SELECT
        u.id,
        :academicYearId,
        FALSE
    FROM users u
    JOIN role r ON r.id = u.role_id
    WHERE r.code = 'MENTOR'
      AND u.account_status = 'ACCEPTED'
      AND u.is_blocked = FALSE
      AND NOT EXISTS (
          SELECT 1
          FROM mentor_participation mp
          WHERE mp.mentor_id = u.id
            AND mp.academic_year_id = :academicYearId
      )
    ON CONFLICT (mentor_id, academic_year_id) DO NOTHING
    """, nativeQuery = true)
    int createMissingForActiveMentors(@Param("academicYearId") Long academicYearId);

    /**
     * Возвращает наставников и их подтверждение участия
     * за выбранный учебный год.
     *
     * Поддерживает поиск по ФИО, МСУ и образовательной организации,
     * а также фильтрацию по признаку участия.
     */
    @Query(
            value = """
        select new org.donriro.nastavnik.mentorship.dto.MentorParticipationAdminResponse(
            mentor.id,
            concat(
                concat(
                    concat(coalesce(mentor.lastName, ''), ' '),
                    coalesce(mentor.firstName, '')
                ),
                concat(' ', coalesce(mentor.middleName, ''))
            ),
            coalesce(organizationMsu.name, directMsu.name),
            educationalOrganization.name,
            participation.participated,
            mentor.accountStatus,
            mentor.blocked
        )
        from MentorParticipation participation
        join participation.mentor mentor
        left join mentor.educationalOrganization educationalOrganization
        left join educationalOrganization.msu organizationMsu
        left join mentor.msu directMsu
        where participation.academicYear.id = :academicYearId
          and (:participated is null or participation.participated = :participated)
          and (
              :search = ''
              or lower(concat(
                  coalesce(mentor.lastName, ''), ' ',
                  coalesce(mentor.firstName, ''), ' ',
                  coalesce(mentor.middleName, '')
              )) like lower(concat('%', :search, '%'))
              or lower(coalesce(organizationMsu.name, directMsu.name, ''))
                    like lower(concat('%', :search, '%'))
              or lower(coalesce(educationalOrganization.name, ''))
                    like lower(concat('%', :search, '%'))
          )
        order by mentor.lastName, mentor.firstName, mentor.middleName
        """,
            countQuery = """
        select count(participation)
        from MentorParticipation participation
        join participation.mentor mentor
        left join mentor.educationalOrganization educationalOrganization
        left join educationalOrganization.msu organizationMsu
        left join mentor.msu directMsu
        where participation.academicYear.id = :academicYearId
          and (:participated is null or participation.participated = :participated)
          and (
              :search = ''
              or lower(concat(
                  coalesce(mentor.lastName, ''), ' ',
                  coalesce(mentor.firstName, ''), ' ',
                  coalesce(mentor.middleName, '')
              )) like lower(concat('%', :search, '%'))
              or lower(coalesce(organizationMsu.name, directMsu.name, ''))
                    like lower(concat('%', :search, '%'))
              or lower(coalesce(educationalOrganization.name, ''))
                    like lower(concat('%', :search, '%'))
          )
        """
    )
    Page<MentorParticipationAdminResponse> findAllForAcademicYear(
            @Param("academicYearId") Long academicYearId,
            @Param("participated") Boolean participated,
            @Param("search") String search,
            Pageable pageable
    );

    /**
     * Возвращает ID учебных годов, в которых хотя бы один наставник подтвердил участие.
     */
    @Query("""
    select distinct participation.academicYear.id
    from MentorParticipation participation
    where participation.academicYear.id in :academicYearIds
      and participation.participated = true
    """)
    Set<Long> findAcademicYearIdsWithConfirmedParticipation(@Param("academicYearIds") Set<Long> academicYearIds);
}
