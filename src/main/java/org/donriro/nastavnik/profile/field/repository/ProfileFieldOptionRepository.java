package org.donriro.nastavnik.profile.field.repository;

import org.donriro.nastavnik.profile.field.entity.ProfileFieldOption;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProfileFieldOptionRepository extends JpaRepository<ProfileFieldOption, Long> {
    List<ProfileFieldOption> findAllByProfileField_Id(Long profileFieldId);
    boolean existsByProfileField_IdAndValue(Long profileFieldId, String value);
    boolean existsByProfileField_IdAndValueAndIdNot(Long profileFieldId, String value, Long id);
    List<ProfileFieldOption> findAllByProfileField_IdOrderBySortOrderAscIdAsc(Long profileFieldId);
    List<ProfileFieldOption> findAllByProfileField_IdAndActiveTrueOrderBySortOrderAscIdAsc(Long profileFieldId);
}
