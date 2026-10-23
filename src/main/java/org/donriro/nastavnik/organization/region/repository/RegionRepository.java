package org.donriro.nastavnik.organization.region.repository;

import org.donriro.nastavnik.organization.region.entity.Region;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RegionRepository extends JpaRepository<Region, Long> {}
