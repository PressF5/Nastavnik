package org.donriro.nastavnik.organization.msu.repository;

import jakarta.persistence.LockModeType;
import org.donriro.nastavnik.organization.msu.entity.Msu;
import org.donriro.nastavnik.user.role.RoleCode;
import org.donriro.nastavnik.user.status.AccountStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface MsuRepository extends JpaRepository<Msu, Long>, JpaSpecificationExecutor<Msu> {

    List<Msu> findAllByRegionIdOrderByNameAsc(Long regionId);
    List<Msu> findAllByOrderByNameAsc();
    boolean existsByIdAndRegionId(Long id, Long regionId);
    boolean existsByRegionIdAndCode(Long regionId, Integer code);
    boolean existsByRegionIdAndCodeAndIdNot(Long regionId, Integer code, Long id);

    /**
     * МСУ, в которых нет администратора со статусом PENDING или ACCEPTED.
     */
    @Query("""
    select m
    from Msu m
    where not exists (
        select u.id
        from User u
        where u.role.code = :roleCode
          and u.msu.id = m.id
          and u.accountStatus in :statuses
    )
    order by m.name
    """)
    List<Msu> findAvailableForMsuAdmin(
            @Param("roleCode") RoleCode roleCode,
            @Param("statuses") Collection<AccountStatus> statuses
    );

    /**
     * МСУ, в которых есть хотя бы одна ОО,
     * доступная для регистрации администратора ОО.
     */
    @Query("""
            select m
            from Msu m
            where exists (
                select eo.id
                from EducationalOrganization eo
                where eo.msu.id = m.id
                  and not exists (
                      select u.id
                      from User u
                      where u.role.code = :roleCode
                        and u.educationalOrganization.id = eo.id
                        and u.accountStatus in :statuses
                  )
            )
            order by m.name
            """)
    List<Msu> findWithAvailableEducationalOrganizations(
            @Param("roleCode") RoleCode roleCode,
            @Param("statuses") Collection<AccountStatus> statuses
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select m
        from Msu m
        where m.id = :id
    """)
    Optional<Msu> findByIdForUpdate(@Param("id") Long id);
}
