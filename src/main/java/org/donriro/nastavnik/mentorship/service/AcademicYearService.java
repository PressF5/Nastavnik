package org.donriro.nastavnik.mentorship.service;

import lombok.RequiredArgsConstructor;
import org.donriro.nastavnik.mentorship.dto.AcademicYearResponse;
import org.donriro.nastavnik.mentorship.dto.CreateAcademicYearRequest;
import org.donriro.nastavnik.mentorship.dto.UpdateAcademicYearRequest;
import org.donriro.nastavnik.mentorship.entity.AcademicYear;
import org.donriro.nastavnik.mentorship.repository.AcademicYearRepository;
import org.donriro.nastavnik.mentorship.repository.MentorParticipationRepository;
import org.donriro.nastavnik.mentorship.repository.MentorshipRepository;
import org.donriro.nastavnik.mentorship.status.AcademicYearStatus;
import org.donriro.nastavnik.security.principal.CustomUserDetails;
import org.donriro.nastavnik.user.entity.User;
import org.donriro.nastavnik.user.role.RoleCode;
import org.donriro.nastavnik.user.status.AccountStatus;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AcademicYearService {

    private final Clock clock;

    private final AcademicYearRepository academicYearRepository;
    private final MentorParticipationRepository mentorParticipationRepository;
    private final MentorshipRepository mentorshipRepository;

    /**
     * Создаёт учебный год.
     * Создание доступно только региональному администратору.
     */
    @Transactional
    public AcademicYearResponse create(CreateAcademicYearRequest request) {
        User currentUser = getCurrentUser();

        requireRegionAdmin(currentUser);

        String name = request.name().trim();

        validatePeriod(request.startDate(), request.endDate());

        if (academicYearRepository.existsByName(name)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Учебный год с таким названием уже существует.");
        }

        if (academicYearRepository.existsOverlappingPeriod(request.startDate(), request.endDate())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Период учебного года пересекается с другим учебным годом.");
        }

        AcademicYear academicYear = new AcademicYear();
        academicYear.setName(name);
        academicYear.setStartDate(request.startDate());
        academicYear.setEndDate(request.endDate());

        AcademicYear saved = academicYearRepository.save(academicYear);

        return toResponse(saved);
    }

    /**
     * Публикует учебный год и создаёт записи участия
     * для всех действующих наставников.
     */
    @Transactional
    public AcademicYearResponse publish(Long id) {
        User currentUser = getCurrentUser();

        requireRegionAdmin(currentUser);

        AcademicYear academicYear = findAcademicYear(id);

        // Повторная публикация не должна создавать дубли.
        if (academicYear.isPublished()) {
            return toResponse(academicYear);
        }

        academicYear.setPublished(true);

        AcademicYear saved = academicYearRepository.save(academicYear);

        // Создаём записи участия непосредственно в PostgreSQL.
        mentorParticipationRepository.createMissingForActiveMentors(saved.getId());

        return toResponse(saved);
    }

    /**
     * Возвращает список учебных годов.
     */
    public List<AcademicYearResponse> getAll() {
        User currentUser = getCurrentUser();

        requireRegionAdmin(currentUser);

        List<AcademicYear> academicYears =
                academicYearRepository.findAllByOrderByStartDateDesc();

        if (academicYears.isEmpty()) {
            return List.of();
        }

        Set<Long> academicYearIds = academicYears.stream()
                .map(AcademicYear::getId)
                .collect(Collectors.toSet());

        Set<Long> yearsWithMentorships =
                mentorshipRepository.findAcademicYearIdsWithMentorships(academicYearIds);

        Set<Long> yearsWithConfirmedParticipation =
                mentorParticipationRepository
                        .findAcademicYearIdsWithConfirmedParticipation(academicYearIds);

        return academicYears.stream()
                .map(year -> toResponse(
                        year,
                        canEdit(year, yearsWithMentorships, yearsWithConfirmedParticipation)
                ))
                .toList();
    }

    /**
     * Возвращает учебные годы, доступные пользователю для просмотра.
     * Наставники и наставляемые не видят будущие учебные годы.
     * Администраторы могут просматривать в том числе запланированные годы.
     */
    public List<AcademicYearResponse> getAvailableForCurrentUser() {
        User currentUser = getCurrentUser();

        if (currentUser.isBlocked()
                || currentUser.getAccountStatus() != AccountStatus.ACCEPTED) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Учётная запись должна быть подтверждена и не заблокирована.");
        }

        RoleCode roleCode = currentUser.getRole() != null ? currentUser.getRole().getCode() : null;

        if (roleCode == null
                || (roleCode != RoleCode.MSU_ADMIN
                && roleCode != RoleCode.OO_ADMIN
                && roleCode != RoleCode.MENTOR
                && roleCode != RoleCode.MENTEE)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "У вас нет доступа к списку учебных годов.");
        }

        List<AcademicYear> academicYears;

        if (roleCode == RoleCode.MENTOR || roleCode == RoleCode.MENTEE) {
            LocalDate today = LocalDate.now(clock);

            academicYears = academicYearRepository
                    .findAllByPublishedTrueAndStartDateLessThanEqualOrderByStartDateDesc(today);
        } else {
            academicYears = academicYearRepository
                    .findAllByPublishedTrueOrderByStartDateDesc();
        }

        return academicYears.stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * Возвращает учебный год по идентификатору.
     * Наставник и наставляемый не могут просматривать будущие учебные годы.
     */
    public AcademicYearResponse getById(Long id) {
        User currentUser = getCurrentUser();

        AcademicYear academicYear = findAcademicYear(id);

        RoleCode roleCode = currentUser.getRole() != null ? currentUser.getRole().getCode() : null;

        // Черновики доступны только региональному администратору.
        if (roleCode != RoleCode.REGION_ADMIN && !academicYear.isPublished()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Учебный год не найден.");
        }

        if (roleCode == RoleCode.MENTOR || roleCode == RoleCode.MENTEE) {
            LocalDate today = LocalDate.now(clock);

            if (academicYear.getStartDate().isAfter(today)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Просмотр будущих учебных годов недоступен.");
            }
        }

        return toResponse(academicYear);
    }

    /**
     * Возвращает учебный год, действующий на текущую дату.
     */
    public AcademicYearResponse getCurrent() {
        getCurrentUser();

        LocalDate today = LocalDate.now(clock);

        AcademicYear academicYear = academicYearRepository.findByDate(today)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "На текущую дату учебный год не найден."));

        return toResponse(academicYear);
    }

    /**
     * Изменяет запланированный учебный год.
     * Активные и завершённые учебные годы редактировать нельзя.
     */
    @Transactional
    public AcademicYearResponse update(Long id, UpdateAcademicYearRequest request) {
        User currentUser = getCurrentUser();

        requireRegionAdmin(currentUser);

        AcademicYear academicYear = findAcademicYear(id);

        // Черновик можно редактировать независимо от календарного статуса.
        // Опубликованный учебный год можно изменять только до начала периода.
        if (!canEdit(academicYear)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Нельзя изменить учебный год: к нему уже привязаны наставнические пары или наставник подтвердил участие.");
        }

        String name = request.name().trim();

        validatePeriod(request.startDate(), request.endDate());

        if (academicYearRepository.existsByNameAndIdNot(name, id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Учебный год с таким названием уже существует.");
        }

        if (academicYearRepository.existsOverlappingPeriodExcludingId(id, request.startDate(), request.endDate())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Период учебного года пересекается с другим учебным годом.");
        }

        academicYear.setName(name);
        academicYear.setStartDate(request.startDate());
        academicYear.setEndDate(request.endDate());

        AcademicYear saved = academicYearRepository.save(academicYear);

        return toResponse(saved);
    }

    /**
     * Удаляет учебный год, если он ещё не связан
     * с наставническими парами и подтверждённым участием наставников.
     */
    @Transactional
    public void delete(Long id) {
        User currentUser = getCurrentUser();

        requireRegionAdmin(currentUser);

        AcademicYear academicYear = findAcademicYear(id);

        // Проверяем наличие наставнических пар независимо от их статуса.
        if (mentorshipRepository.existsByAcademicYear_Id(id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Нельзя удалить учебный год, к которому уже привязаны наставнические пары.");
        }

        // Проверяем, подтвердил ли хотя бы один наставник участие.
        if (mentorParticipationRepository.existsByAcademicYear_IdAndParticipatedTrue(id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Нельзя удалить учебный год: хотя бы один наставник уже подтвердил участие.");
        }

        // Удаляем автоматически созданные неподтверждённые записи участия.
        mentorParticipationRepository.deleteAllByAcademicYearId(id);

        // Удаляем сам учебный год.
        academicYearRepository.delete(academicYear);
    }

    /**
     * Проверяет, разрешено ли редактирование учебного года.
     * Публикация не ограничивает редактирование, если год ещё не связан
     * с наставническими парами и никто не подтвердил участие.
     */
    private boolean canEdit(AcademicYear academicYear, Set<Long> yearsWithMentorships, Set<Long> yearsWithConfirmedParticipation) {
        Long id = academicYear.getId();

        return !yearsWithMentorships.contains(id) && !yearsWithConfirmedParticipation.contains(id);
    }

    /**
     * Проверяет возможность редактирования одного учебного года.
     */
    private boolean canEdit(AcademicYear academicYear) {
        Long id = academicYear.getId();
        return !mentorshipRepository.existsByAcademicYear_Id(id) && !mentorParticipationRepository.existsByAcademicYear_IdAndParticipatedTrue(id);
    }

    /**
     * Определяет статус учебного года по календарной дате.
     */
    private AcademicYearStatus getStatus(AcademicYear academicYear) {
        LocalDate today = LocalDate.now(clock);

        if (today.isBefore(academicYear.getStartDate())) {
            return AcademicYearStatus.PLANNED;
        }

        if (!today.isAfter(academicYear.getEndDate())) {
            return AcademicYearStatus.ACTIVE;
        }

        return AcademicYearStatus.COMPLETED;
    }

    private AcademicYearResponse toResponse(AcademicYear academicYear) {
        return toResponse(academicYear, canEdit(academicYear));
    }

    private AcademicYearResponse toResponse(AcademicYear academicYear, boolean canEdit) {
        return new AcademicYearResponse(
                academicYear.getId(),
                academicYear.getName(),
                academicYear.isPublished(),
                academicYear.getStartDate(),
                academicYear.getEndDate(),
                getStatus(academicYear),
                canEdit
        );
    }

    private void validatePeriod(LocalDate startDate, LocalDate endDate) {
        if (!startDate.isBefore(endDate)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Дата начала учебного года должна быть раньше даты окончания.");
        }
    }

    private AcademicYear findAcademicYear(Long id) {
        return academicYearRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Учебный год не найден."));
    }

    private User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !(authentication.getPrincipal() instanceof CustomUserDetails userDetails)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Необходимо авторизоваться.");
        }

        return userDetails.getUser();
    }

    private void requireRegionAdmin(User user) {
        if (user.getRole() == null || user.getRole().getCode() != RoleCode.REGION_ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Управлять учебными годами может только региональный администратор.");
        }

        if (user.isBlocked() || user.getAccountStatus() != AccountStatus.ACCEPTED) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Учётная запись должна быть подтверждена и не заблокирована.");
        }
    }
}