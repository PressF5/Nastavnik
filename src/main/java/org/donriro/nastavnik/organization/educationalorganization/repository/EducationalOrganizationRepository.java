package org.donriro.nastavnik.organization.educationalorganization.repository;

import org.donriro.nastavnik.organization.educationalorganization.entity.EducationalOrganization;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EducationalOrganizationRepository extends JpaRepository<EducationalOrganization, Long> {
    boolean existsByMsuIdAndCodeAndIdNot(Long msuId, Integer code, Long id);
    boolean existsByMsuId(Long msuId);
    List<EducationalOrganization> findAllByMsuIdOrderByNameAsc(Long msuId);
    boolean existsByIdAndMsuId(Long id, Long msuId);
    boolean existsByIdAndMsuRegionId(Long id, Long regionId);
    boolean existsByMsuIdAndCode(Long msuId, Integer code);
    Page<EducationalOrganization> findAllByMsu_IdAndMsu_Region_IdOrderByNameAsc(Long msuId, Long regionId, Pageable pageable);
}
