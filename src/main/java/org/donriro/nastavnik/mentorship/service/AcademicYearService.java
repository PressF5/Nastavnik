package org.donriro.nastavnik.mentorship.service;

import lombok.RequiredArgsConstructor;
import org.donriro.nastavnik.mentorship.dto.AcademicYearResponse;
import org.donriro.nastavnik.mentorship.dto.CreateAcademicYearRequest;
import org.donriro.nastavnik.mentorship.dto.UpdateAcademicYearRequest;
import org.donriro.nastavnik.mentorship.entity.AcademicYear;
import org.donriro.nastavnik.mentorship.entity.MentorParticipation;
import org.donriro.nastavnik.mentorship.repository.AcademicYearRepository;
import org.donriro.nastavnik.mentorship.repository.MentorParticipationRepository;
import org.donriro.nastavnik.mentorship.repository.MentorshipRepository;
import org.donriro.nastavnik.mentorship.status.AcademicYearStatus;
import org.donriro.nastavnik.security.principal.CustomUserDetails;
import org.donriro.nastavnik.user.entity.User;
import org.donriro.nastavnik.user.repository.UserRepository;
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

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AcademicYearService {

    private final Clock clock;

    private final AcademicYearRepository academicYearRepository;
    private final MentorParticipationRepository mentorParticipationRepository;
    private final UserRepository userRepository;
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

        // Получаем всех действующих наставников.
        List<User> mentors = userRepository.findAllByRole_CodeAndAccountStatusAndBlockedFalse(RoleCode.MENTOR, AccountStatus.ACCEPTED);

        // Создаём отдельную запись участия для каждого наставника.
        List<MentorParticipation> participations = mentors.stream()
                .map(mentor -> {
                    MentorParticipation participation = new MentorParticipation();
                    participation.setMentor(mentor);
                    participation.setAcademicYear(saved);
                    participation.setParticipated(false);
                    return participation;
                })
                .toList();

        // Сохраняем записи участия вместе с созданием учебного года.
        mentorParticipationRepository.saveAll(participations);

        return toResponse(saved);
    }

    /**
     * Возвращает список учебных годов.
     */
    public List<AcademicYearResponse> getAll() {
        User currentUser = getCurrentUser();

        requireRegionAdmin(currentUser);

        return academicYearRepository.findAllByOrderByStartDateDesc()
                .stream()
                .map(this::toResponse)
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
                    .findAllByStartDateLessThanEqualOrderByStartDateDesc(today);
        } else {
            academicYears = academicYearRepository.findAllByOrderByStartDateDesc();
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

        if (getStatus(academicYear) != AcademicYearStatus.PLANNED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Можно изменять только запланированный учебный год.");
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
     * Удаляет запланированный учебный год, если он ещё не связан
     * с наставническими парами и подтверждённым участием наставников.
     */
    @Transactional
    public void delete(Long id) {
        User currentUser = getCurrentUser();

        requireRegionAdmin(currentUser);

        AcademicYear academicYear = findAcademicYear(id);

        if (getStatus(academicYear) != AcademicYearStatus.PLANNED) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Удалять можно только запланированный учебный год."
            );
        }

        // Проверяем наличие наставнических пар независимо от их статуса.
        if (mentorshipRepository.existsByAcademicYear_Id(id)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Нельзя удалить учебный год, к которому уже привязаны наставнические пары."
            );
        }

        // Проверяем, подтвердил ли хотя бы один наставник участие.
        if (mentorParticipationRepository.existsByAcademicYear_IdAndParticipatedTrue(id)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Нельзя удалить учебный год: хотя бы один наставник уже подтвердил участие."
            );
        }

        // Удаляем автоматически созданные неподтверждённые записи участия.
        mentorParticipationRepository.deleteAllByAcademicYearId(id);

        // Удаляем сам учебный год.
        academicYearRepository.delete(academicYear);
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
        return new AcademicYearResponse(
                academicYear.getId(),
                academicYear.getName(),
                academicYear.getStartDate(),
                academicYear.getEndDate(),
                getStatus(academicYear)
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