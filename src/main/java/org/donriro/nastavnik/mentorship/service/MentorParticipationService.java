package org.donriro.nastavnik.mentorship.service;

import lombok.RequiredArgsConstructor;
import org.donriro.nastavnik.mentorship.dto.MentorParticipationResponse;
import org.donriro.nastavnik.mentorship.dto.UpdateMentorParticipationRequest;
import org.donriro.nastavnik.mentorship.entity.AcademicYear;
import org.donriro.nastavnik.mentorship.entity.MentorParticipation;
import org.donriro.nastavnik.mentorship.repository.AcademicYearRepository;
import org.donriro.nastavnik.mentorship.repository.MentorParticipationRepository;
import org.donriro.nastavnik.mentorship.repository.MentorshipRepository;
import org.donriro.nastavnik.mentorship.status.MentorshipStatus;
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
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MentorParticipationService {

    private final Clock clock;

    private final AcademicYearRepository academicYearRepository;
    private final MentorParticipationRepository mentorParticipationRepository;
    private final UserRepository userRepository;
    private final MentorshipRepository mentorshipRepository;

    /**
     * Создаёт отсутствующие записи участия наставника
     * во всех учебных годах, которые ещё не завершились.
     */
    @Transactional
    public void ensureParticipationForNotCompletedYears(User mentor) {
        if (mentor.getRole() == null
                || mentor.getRole().getCode() != RoleCode.MENTOR
                || mentor.getAccountStatus() != AccountStatus.ACCEPTED
                || mentor.isBlocked()) {
            return;
        }

        LocalDate today = LocalDate.now(clock);

        List<AcademicYear> academicYears = academicYearRepository.findAllByEndDateGreaterThanEqualOrderByStartDateAsc(today);
        List<MentorParticipation> newParticipations = new ArrayList<>();

        for (AcademicYear academicYear : academicYears) {
            boolean alreadyExists = mentorParticipationRepository
                    .findByMentorIdAndAcademicYearId(mentor.getId(), academicYear.getId())
                    .isPresent();

            if (alreadyExists) {
                continue;
            }

            MentorParticipation participation = new MentorParticipation();
            participation.setMentor(mentor);
            participation.setAcademicYear(academicYear);
            participation.setParticipated(false);

            newParticipations.add(participation);
        }

        if (!newParticipations.isEmpty()) {
            mentorParticipationRepository.saveAll(newParticipations);
        }
    }

    /**
     * Возвращает подтверждение участия наставника
     * в текущем учебном году.
     */
    @Transactional
    public MentorParticipationResponse getCurrentParticipation() {
        User mentor = getCurrentMentor();

        LocalDate today = LocalDate.now(clock);

        AcademicYear academicYear = academicYearRepository.findByDate(today)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "На текущую дату не найден действующий учебный год."));

        // Проверяем наличие записи участия.
        ensureParticipationForNotCompletedYears(mentor);

        MentorParticipation participation = mentorParticipationRepository
                .findByMentorIdAndAcademicYearId(mentor.getId(), academicYear.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Запись участия наставника не найдена."));

        return toResponse(participation);
    }

    /**
     * Изменяет подтверждение участия наставника
     * в текущем учебном году.
     */
    @Transactional
    public MentorParticipationResponse updateCurrentParticipation(UpdateMentorParticipationRequest request) {
        User currentMentor = getCurrentMentor();

        // Блокируем строку наставника.
        // Создание пары и изменение участия должны выполняться последовательно.
        User mentor = userRepository.findByIdForUpdate(currentMentor.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Наставник не найден."));

        LocalDate today = LocalDate.now(clock);

        AcademicYear academicYear = academicYearRepository.findByDate(today)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "На текущую дату не найден действующий учебный год."));

        ensureParticipationForNotCompletedYears(mentor);

        MentorParticipation participation = mentorParticipationRepository
                .findByMentorIdAndAcademicYearId(mentor.getId(), academicYear.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Запись участия наставника не найдена."));

        boolean participated = request.participated();

        // Нельзя отказаться от участия, пока есть действующие наставляемые в этом году.
        if (!participated && mentorshipRepository
                .countByMentor_IdAndAcademicYear_IdAndStatus(mentor.getId(), academicYear.getId(), MentorshipStatus.ACCEPTED) > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Нельзя отменить участие, пока у вас есть действующие наставляемые в текущем учебном году.");
        }

        participation.setParticipated(participated);

        return toResponse(mentorParticipationRepository.save(participation));
    }

    /**
     * Получает текущего авторизованного наставника.
     */
    private User getCurrentMentor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !(authentication.getPrincipal() instanceof CustomUserDetails userDetails)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Необходимо авторизоваться.");
        }

        User mentor = userDetails.getUser();

        if (mentor.getRole() == null || mentor.getRole().getCode() != RoleCode.MENTOR) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Операция доступна только наставнику.");
        }

        if (mentor.getAccountStatus() != AccountStatus.ACCEPTED || mentor.isBlocked()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Учётная запись наставника должна быть подтверждена и не заблокирована.");
        }

        return mentor;
    }

    /**
     * Преобразует запись участия в DTO.
     */
    private MentorParticipationResponse toResponse(MentorParticipation participation) {
        AcademicYear academicYear = participation.getAcademicYear();

        return new MentorParticipationResponse(
                academicYear.getId(),
                academicYear.getName(),
                academicYear.getStartDate(),
                academicYear.getEndDate(),
                participation.isParticipated()
        );
    }
}
