package org.donriro.nastavnik.profile.field.repository;

import org.donriro.nastavnik.profile.value.entity.ProfileValueOption;
import org.donriro.nastavnik.profile.value.entity.ProfileValueOptionId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProfileValueOptionRepository extends JpaRepository<ProfileValueOption, ProfileValueOptionId> {
    List<ProfileValueOption> findAllByProfileValue_Id(Long profileValueId);
    void deleteAllByProfileValue_Id(Long profileValueId);
}
