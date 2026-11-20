package org.donriro.nastavnik.mentorship.repository;

import org.donriro.nastavnik.mentorship.entity.AcademicYear;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface AcademicYearRepository extends JpaRepository<AcademicYear, Long> {

    /**
     * Находит учебный год, в период которого входит указанная дата.
     */
    @Query("""
            SELECT ay
            FROM AcademicYear ay
            WHERE :date BETWEEN ay.startDate AND ay.endDate
            """)
    Optional<AcademicYear> findByDate(@Param("date") LocalDate date);

    /**
     * Проверяет, пересекается ли новый период с существующими учебными годами.
     */
    @Query("""
            SELECT COUNT(ay) > 0
            FROM AcademicYear ay
            WHERE ay.startDate <= :endDate
              AND ay.endDate >= :startDate
            """)
    boolean existsOverlappingPeriod(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);
    List<AcademicYear> findAllByOrderByStartDateDesc();
}
