package org.donriro.nastavnik.user.repository;

import org.donriro.nastavnik.user.entity.User;
import org.donriro.nastavnik.user.role.RoleCode;
import org.donriro.nastavnik.user.status.AccountStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
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
    """)
    List<User> findEligibleMentors(
            @Param("roleCode") RoleCode roleCode,
            @Param("msuId") Long msuId,
            @Param("status") AccountStatus status
    );

    @EntityGraph(attributePaths = {"role", "region", "msu", "educationalOrganization"})
    @Override
    Page<User> findAll(Specification<User> spec, Pageable pageable);
}
