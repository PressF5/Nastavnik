package org.donriro.nastavnik.user.repository;

import org.donriro.nastavnik.user.entity.Role;
import org.donriro.nastavnik.user.role.RoleCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RoleRepository extends JpaRepository<Role, Short> {

    Optional<Role> findByCode(RoleCode code);
}
