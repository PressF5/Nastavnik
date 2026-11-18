package org.donriro.nastavnik.profile.value.repository;

import org.donriro.nastavnik.profile.value.entity.ProfileValue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface ProfileValueRepository extends JpaRepository<ProfileValue, Long> {
    Optional<ProfileValue> findByUser_IdAndProfileField_Id(Long userId, Long profileFieldId);
    List<ProfileValue> findAllByUser_Id(Long userId);
    boolean existsByProfileField_Id(Long profileFieldId);
    /**
     * Возвращает ID пользователей, которые подтвердили готовность
     * к наставничеству.
     */
    @Query("""
        select distinct pv.user.id
        from ProfileValue pv
        where pv.user.id in :userIds
          and pv.profileField.name = :fieldName
          and pv.profileField.active = true
          and pv.valueBoolean = true
        """)
    Set<Long> findUserIdsWithTrueBooleanValue(@Param("userIds") Set<Long> userIds, @Param("fieldName") String fieldName);
}
