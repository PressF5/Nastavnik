package org.donriro.nastavnik.profile.field.repository;

import org.donriro.nastavnik.profile.field.entity.ProfileFieldOption;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProfileFieldOptionRepository extends JpaRepository<ProfileFieldOption, Long> {
    List<ProfileFieldOption> findAllByProfileField_Id(Long profileFieldId);
    Optional<ProfileFieldOption> findByIdAndProfileField_Id(Long id, Long profileFieldId);
    boolean existsByProfileField_IdAndValue(Long profileFieldId, String value);
    boolean existsByProfileField_IdAndValueAndIdNot(Long profileFieldId, String value, Long id);
    boolean existsByProfileField_Id(Long profileFieldId);
    List<ProfileFieldOption> findAllByProfileField_IdOrderBySortOrderAscIdAsc(Long profileFieldId);
    List<ProfileFieldOption> findAllByProfileField_IdAndActiveTrueOrderBySortOrderAscIdAsc(Long profileFieldId);
}
