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
     * Проверяет пересечение периода с любым существующим учебным годом.
     */
    @Query("""
            SELECT COUNT(ay) > 0
            FROM AcademicYear ay
            WHERE ay.startDate <= :endDate
              AND ay.endDate >= :startDate
            """)
    boolean existsOverlappingPeriod(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    /**
     * Проверяет пересечение при редактировании, исключая сам учебный год.
     */
    @Query("""
            SELECT COUNT(ay) > 0
            FROM AcademicYear ay
            WHERE ay.id <> :id
              AND ay.startDate <= :endDate
              AND ay.endDate >= :startDate
            """)
    boolean existsOverlappingPeriodExcludingId(
            @Param("id") Long id,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    boolean existsByName(String name);
    boolean existsByNameAndIdNot(String name, Long id);
    List<AcademicYear> findAllByOrderByStartDateDesc();

    /**
     * Возвращает учебные годы, которые ещё не завершились.
     * Включает запланированные и активные учебные годы.
     */
    List<AcademicYear> findAllByEndDateGreaterThanEqualOrderByStartDateAsc(LocalDate today);

    /**
     * Возвращает учебные годы, которые уже начались.
     * Включает текущие и завершённые годы, исключает запланированные.
     */
    List<AcademicYear> findAllByStartDateLessThanEqualOrderByStartDateDesc(LocalDate today);
}