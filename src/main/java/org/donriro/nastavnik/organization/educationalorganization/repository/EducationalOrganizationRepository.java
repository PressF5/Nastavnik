package org.donriro.nastavnik.organization.educationalorganization.repository;

import org.donriro.nastavnik.organization.educationalorganization.entity.EducationalOrganization;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EducationalOrganizationRepository extends JpaRepository<EducationalOrganization, Long> {

    List<EducationalOrganization> findAllByMsuIdOrderByNameAsc(Long msuId);
    boolean existsByIdAndMsuId(Long id, Long msuId);
    boolean existsByIdAndMsuRegionId(Long id, Long regionId);
    boolean existsByMsuIdAndCode(Long msuId, Integer code);
}
