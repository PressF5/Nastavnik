package org.donriro.nastavnik.mentorship.service;

import lombok.RequiredArgsConstructor;
import org.donriro.nastavnik.mentorship.dto.MentorParticipationAdminResponse;
import org.donriro.nastavnik.mentorship.repository.AcademicYearRepository;
import org.donriro.nastavnik.mentorship.repository.MentorParticipationRepository;
import org.donriro.nastavnik.security.principal.CustomUserDetails;
import org.donriro.nastavnik.user.entity.User;
import org.donriro.nastavnik.user.role.RoleCode;
import org.donriro.nastavnik.user.status.AccountStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminMentorParticipationService {

    private final AcademicYearRepository academicYearRepository;
    private final MentorParticipationRepository mentorParticipationRepository;

    /**
     * Возвращает список подтверждений участия наставников
     * за выбранный учебный год.
     */
    public Page<MentorParticipationAdminResponse> getAll(Long academicYearId, Boolean participated, String search, int page, int size) {
        User currentUser = getCurrentUser();
        requireRegionAdmin(currentUser);

        if (!academicYearRepository.existsById(academicYearId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Учебный год не найден.");
        }

        if (page < 0 || size < 1 || size > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Некорректные параметры пагинации.");
        }

        String normalizedSearch = search == null ? "" : search.trim();

        return mentorParticipationRepository.findAllForAcademicYear(academicYearId, participated, normalizedSearch, PageRequest.of(page, size));
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
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Операция доступна только региональному администратору.");
        }

        if (user.isBlocked() || user.getAccountStatus() != AccountStatus.ACCEPTED) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Учётная запись должна быть подтверждена и не заблокирована.");
        }
    }
}