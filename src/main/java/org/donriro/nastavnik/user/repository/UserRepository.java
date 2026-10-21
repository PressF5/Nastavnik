package org.donriro.nastavnik.user.repository;

import jakarta.persistence.LockModeType;
import org.donriro.nastavnik.mentorship.status.MentorshipStatus;
import org.donriro.nastavnik.user.entity.User;
import org.donriro.nastavnik.user.role.RoleCode;
import org.donriro.nastavnik.user.status.AccountStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {

    boolean existsByEmail(String email);
    boolean existsByRoleCode(RoleCode roleCode);
    boolean existsByRoleCodeAndMsuId(RoleCode roleCode, Long msuId);
    boolean existsByRoleCodeAndEducationalOrganizationId(RoleCode roleCode, Long educationalOrganizationId);
    Optional<User> findByEmail(String email);
    List<User> findAllByRoleCode(RoleCode roleCode);
    List<User> findAllByMsuId(Long msuId);
    List<User> findAllByEducationalOrganizationId(Long educationalOrganizationId);
    List<User> findAllByAccountStatus(AccountStatus status);

    @Query("""
        select u
        from User u
        join fetch u.role
        join fetch u.region
        left join fetch u.msu
        left join fetch u.educationalOrganization
        where u.email = :email
        """)
    Optional<User> findByEmailWithSecurityData(@Param("email") String email);

    /**
     * Возвращает подтверждённых и незаблокированных наставников
     * указанного МСУ, у которых ещё есть свободное место.
     *
     * Наставник может иметь не более трёх действующих наставляемых.
     * Ожидающие заявки PENDING не учитываются.
     */
    @Query("""
    select u
    from User u
    join fetch u.role
    join fetch u.region
    left join fetch u.msu
    left join fetch u.educationalOrganization
    where u.role.code = :roleCode
      and u.msu.id = :msuId
      and u.accountStatus = :status
      and u.blocked = false
      and (
          select count(m.id)
          from Mentorship m
          where m.mentor.id = u.id
            and m.status = :activeStatus
      ) < :maxActiveMentees
    order by u.lastName, u.firstName, u.middleName
    """)
    List<User> findEligibleMentors(
            @Param("roleCode") RoleCode roleCode,
            @Param("msuId") Long msuId,
            @Param("status") AccountStatus status,
            @Param("activeStatus") MentorshipStatus activeStatus,
            @Param("maxActiveMentees") long maxActiveMentees
    );
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select u
            from User u
            where u.id = :id
            """)
    Optional<User> findByIdForUpdate(@Param("id") Long id);
    /**
     * Возвращает наставляемых, которым можно назначить наставника.
     * Исключаются пользователи:
     * - с ожидающей заявкой PENDING;
     * - с действующим наставничеством ACCEPTED.
     * Учитываются только подтверждённые и незаблокированные
     * аккаунты из указанной образовательной организации.
     */
    @Query("""
    select u
    from User u
    join fetch u.role
    left join fetch u.msu
    left join fetch u.educationalOrganization
    where u.role.code = :roleCode
      and u.educationalOrganization.id = :organizationId
      and u.accountStatus = :accountStatus
      and u.blocked = false
      and not exists (
          select m.id
          from Mentorship m
          where m.mentee.id = u.id
            and m.status in :excludedStatuses
      )
    order by u.lastName, u.firstName, u.middleName
    """)
    List<User> findAvailableMentees(
            @Param("roleCode") RoleCode roleCode,
            @Param("organizationId") Long organizationId,
            @Param("accountStatus") AccountStatus accountStatus,
            @Param("excludedStatuses") List<MentorshipStatus> excludedStatuses
    );
    @EntityGraph(attributePaths = {"role", "region", "msu", "educationalOrganization"})
    @Override
    Page<User> findAll(Specification<User> spec, Pageable pageable);
}
