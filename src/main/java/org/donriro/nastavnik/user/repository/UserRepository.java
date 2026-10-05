package org.donriro.nastavnik.user.repository;

import org.donriro.nastavnik.user.entity.User;
import org.donriro.nastavnik.user.role.RoleCode;
import org.donriro.nastavnik.user.status.AccountStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    boolean existsByEmail(String email);
    boolean existsByRoleCode(RoleCode roleCode);
    boolean existsByRoleCodeAndMsuId(RoleCode roleCode, Long msuId);
    boolean existsByRoleCodeAndEducationalOrganizationId(RoleCode roleCode, Long educationalOrganizationId);
    Optional<User> findByEmail(String email);
    List<User> findAllByRoleCode(RoleCode roleCode);
    List<User> findAllByMsuId(Long msuId);
    List<User> findAllByEducationalOrganizationId(Long educationalOrganizationId);
    List<User> findAllByAccountStatus(AccountStatus status);
}
