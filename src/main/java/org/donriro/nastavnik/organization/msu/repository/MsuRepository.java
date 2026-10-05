package org.donriro.nastavnik.organization.msu.repository;

import org.donriro.nastavnik.organization.msu.entity.Msu;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MsuRepository extends JpaRepository<Msu, Long> {

    List<Msu> findAllByRegionId(Long regionId);
    boolean existsByIdAndRegionId(Long id, Long regionId);
    boolean existsByRegionIdAndCode(Long regionId, Integer code);
}
