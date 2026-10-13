package org.donriro.nastavnik.profile.field.repository;

import io.micrometer.common.KeyValues;
import org.donriro.nastavnik.profile.field.entity.ProfileField;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProfileFieldRepository extends JpaRepository<ProfileField, Long> {
    boolean existsByNameIgnoreCase(String name);
    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);
}
