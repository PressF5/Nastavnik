package org.donriro.nastavnik.profile.field.repository;

import org.donriro.nastavnik.profile.field.entity.ProfileFieldRole;
import org.donriro.nastavnik.profile.field.entity.ProfileFieldRoleId;
import org.donriro.nastavnik.user.role.RoleCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProfileFieldRoleRepository extends JpaRepository<ProfileFieldRole, ProfileFieldRoleId> {
    List<ProfileFieldRole> findAllByProfileField_Id(Long profileFieldId);
    boolean existsByProfileField_IdAndRole_Code(Long profileFieldId, RoleCode roleCode);
    List<ProfileFieldRole> findAllByRole_CodeAndProfileField_ActiveTrueOrderBySortOrderAsc(RoleCode roleCode);
}
