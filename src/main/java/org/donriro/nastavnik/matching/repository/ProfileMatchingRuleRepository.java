package org.donriro.nastavnik.matching.repository;

import org.donriro.nastavnik.matching.entity.ProfileMatchingRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProfileMatchingRuleRepository extends JpaRepository<ProfileMatchingRule, Long> {
    Optional<ProfileMatchingRule> findByProfileField_Id(Long profileFieldId);
    List<ProfileMatchingRule> findAllByActiveTrue();
    boolean existsByProfileField_Id(Long profileFieldId);
}
