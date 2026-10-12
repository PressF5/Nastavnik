package org.donriro.nastavnik.mentorship.repository;

import org.donriro.nastavnik.mentorship.entity.Mentorship;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MentorshipRepository extends JpaRepository<Mentorship, Long> {
}
