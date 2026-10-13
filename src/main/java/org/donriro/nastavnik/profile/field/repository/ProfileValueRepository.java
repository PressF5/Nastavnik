package org.donriro.nastavnik.profile.field.repository;

import org.donriro.nastavnik.profile.value.entity.ProfileValue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProfileValueRepository extends JpaRepository<ProfileValue, Long> {
    Optional<ProfileValue> findByUser_IdAndProfileField_Id(Long userId, Long profileFieldId);
    List<ProfileValue> findAllByUser_Id(Long userId);
    boolean existsByUser_IdAndProfileField_Id(Long userId, Long profileFieldId);
}
