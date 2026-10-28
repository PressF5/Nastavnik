package org.donriro.nastavnik.organization.educationalorganization.repository;

import jakarta.persistence.LockModeType;
import org.donriro.nastavnik.organization.educationalorganization.entity.EducationalOrganization;
import org.donriro.nastavnik.user.role.RoleCode;
import org.donriro.nastavnik.user.status.AccountStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
public interface EducationalOrganizationRepository extends JpaRepository<EducationalOrganization, Long>, JpaSpecificationExecutor<EducationalOrganization> {
    boolean existsByMsuIdAndCodeAndIdNot(Long msuId, Integer code, Long id);
    boolean existsByMsuId(Long msuId);
    List<EducationalOrganization> findAllByMsuIdOrderByNameAsc(Long msuId);
    boolean existsByIdAndMsuId(Long id, Long msuId);
    boolean existsByIdAndMsuRegionId(Long id, Long regionId);
    boolean existsByMsuIdAndCode(Long msuId, Integer code);
    Page<EducationalOrganization> findAllByMsu_IdAndMsu_Region_IdOrderByNameAsc(Long msuId, Long regionId, Pageable pageable);

    /**
     * ОО, в которых ещё нет администратора с активной заявкой
     * или подтверждённым аккаунтом.
     */
    @Query("""
        select eo
        from EducationalOrganization eo
        where eo.msu.id = :msuId
          and not exists (
              select u.id
              from User u
              where u.role.code = :roleCode
                and u.educationalOrganization.id = eo.id
                and u.accountStatus in :statuses
          )
        order by eo.name
    """)
    List<EducationalOrganization> findAvailableForOoAdmin(
            @Param("msuId") Long msuId,
            @Param("roleCode") RoleCode roleCode,
            @Param("statuses") Collection<AccountStatus> statuses
    );

    /**
     * Блокирует выбранную ОО на время транзакции регистрации.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select eo
        from EducationalOrganization eo
        where eo.id = :id
          and eo.msu.id = :msuId
    """)
    Optional<EducationalOrganization> findByIdAndMsuIdForUpdate(
            @Param("id") Long id,
            @Param("msuId") Long msuId
    );
}
