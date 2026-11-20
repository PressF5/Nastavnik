package org.donriro.nastavnik.mentorship.service;

import lombok.RequiredArgsConstructor;
import org.donriro.nastavnik.mentorship.entity.AcademicYear;
import org.donriro.nastavnik.mentorship.entity.MentorParticipation;
import org.donriro.nastavnik.mentorship.repository.AcademicYearRepository;
import org.donriro.nastavnik.mentorship.repository.MentorParticipationRepository;
import org.donriro.nastavnik.user.entity.User;
import org.donriro.nastavnik.user.role.RoleCode;
import org.donriro.nastavnik.user.status.AccountStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
}
