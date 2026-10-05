package org.donriro.nastavnik.organization.region.repository;

import org.donriro.nastavnik.organization.region.entity.Region;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RegionRepository extends JpaRepository<Region, Long> {

    boolean existsByCode(Integer code);
    Optional<Region> findByCode(Integer code);
}
