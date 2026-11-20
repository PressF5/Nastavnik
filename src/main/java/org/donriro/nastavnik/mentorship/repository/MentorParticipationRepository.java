package org.donriro.nastavnik.mentorship.repository;

import org.donriro.nastavnik.mentorship.entity.MentorParticipation;
import org.donriro.nastavnik.user.entity.User;
import org.donriro.nastavnik.user.role.RoleCode;
import org.donriro.nastavnik.user.status.AccountStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MentorParticipationRepository extends JpaRepository<MentorParticipation, Long> {

    /**
     * Возвращает действующих наставников:
     * подтверждённых и незаблокированных пользователей с ролью MENTOR.
     */
    @Query("""
            SELECT u
            FROM User u
            WHERE u.role.code = :roleCode
              AND u.accountStatus = :accountStatus
              AND u.blocked = false
            """)
    List<User> findEligibleMentors(RoleCode roleCode, AccountStatus accountStatus);

    /**
     * Находит подтверждение участия наставника в конкретном учебном году.
     */
    Optional<MentorParticipation> findByMentorIdAndAcademicYearId(Long mentorId, Long academicYearId);
}