package org.donriro.nastavnik.user.service;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.donriro.nastavnik.organization.educationalorganization.entity.EducationalOrganization;
import org.donriro.nastavnik.organization.educationalorganization.repository.EducationalOrganizationRepository;
import org.donriro.nastavnik.organization.msu.entity.Msu;
import org.donriro.nastavnik.organization.msu.repository.MsuRepository;
import org.donriro.nastavnik.organization.region.entity.Region;
import org.donriro.nastavnik.user.dto.request.RegistrationRequest;
import org.donriro.nastavnik.user.entity.Role;
import org.donriro.nastavnik.user.entity.User;
import org.donriro.nastavnik.user.exception.*;
import org.donriro.nastavnik.user.repository.RoleRepository;
import org.donriro.nastavnik.user.repository.UserRepository;
import org.donriro.nastavnik.user.role.RoleCode;
import org.donriro.nastavnik.user.status.AccountStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
@RequiredArgsConstructor
@Transactional
public class RegistrationService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final MsuRepository msuRepository;
    private final EducationalOrganizationRepository educationalOrganizationRepository;
    private final PasswordEncoder passwordEncoder;

    public User register(RegistrationRequest request) {
        RoleCode roleCode = request.role();
        Role role = getRole(roleCode);

        String normalizedEmail = normalizeEmail(request.email());
        checkEmailUniqueness(normalizedEmail);

        return switch (roleCode) {
            case MSU_ADMIN -> registerMsuAdmin(request, normalizedEmail, role);
            case OO_ADMIN -> registerOoAdmin(request, normalizedEmail, role);
            case MENTOR -> registerMentor(request, normalizedEmail, role);
            case MENTEE -> registerMentee(request, normalizedEmail, role);
            case REGION_ADMIN -> throw new InvalidRegistrationRoleException("Регистрация REGION_ADMIN через обычную регистрацию запрещена.");
        };
    }

    private User registerMsuAdmin(RegistrationRequest request, String email, Role role) {
        Msu msu = getMsu(request.msuId());
        checkMsuAdminUniqueness(msu);

        User user = buildUser(request, email, role, msu.getRegion(), msu, null);

        return userRepository.save(user);
    }

    private User registerOoAdmin(RegistrationRequest request, String email, Role role) {
        EducationalOrganization educationalOrganization = getEducationalOrganization(request.educationalOrganizationId(), request.msuId());
        Msu msu = educationalOrganization.getMsu();
        Region region = msu.getRegion();

        checkOoAdminUniqueness(educationalOrganization);

        User user = buildUser(request, email, role, region, msu, educationalOrganization);

        return userRepository.save(user);
    }

    private User registerMentor(RegistrationRequest request, String email, Role role) {
        EducationalOrganization educationalOrganization = getEducationalOrganization(request.educationalOrganizationId(), request.msuId());
        Msu msu = educationalOrganization.getMsu();
        Region region = msu.getRegion();

        User user = buildUser(request, email, role, region, msu, educationalOrganization);

        return userRepository.save(user);
    }

    private User registerMentee(RegistrationRequest request, String email, Role role) {
        EducationalOrganization educationalOrganization = getEducationalOrganization(request.educationalOrganizationId(), request.msuId());
        Msu msu = educationalOrganization.getMsu();
        Region region = msu.getRegion();

        User user = buildUser(request, email, role, region, msu, educationalOrganization);

        return userRepository.save(user);
    }

    private User buildUser(RegistrationRequest request, String email, Role role, Region region, Msu msu, EducationalOrganization educationalOrganization) {
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setFirstName(request.firstName().trim());
        user.setLastName(request.lastName().trim());

        String middleName = request.middleName();
        if (middleName != null && !middleName.isBlank()) {
            user.setMiddleName(middleName.trim());
        }

        user.setBirthDate(request.birthDate());
        user.setRegion(region);
        user.setMsu(msu);
        user.setEducationalOrganization(educationalOrganization);
        user.setRole(role);
        user.setAccountStatus(AccountStatus.PENDING);
        user.setBlocked(false);

        return user;
    }

    private void checkEmailUniqueness(String email) {
        if (userRepository.existsByEmail(email)) {
            throw new EmailAlreadyExistsException("Пользователь с таким email уже зарегистрирован.");
        }
    }

    private void checkMsuAdminUniqueness(Msu msu) {
        if (userRepository.existsByRoleCodeAndMsuId(RoleCode.MSU_ADMIN, msu.getId())) {
            throw new AdministratorAlreadyExistsException("Для данного МСУ уже существует администратор.");
        }
    }

    private void checkOoAdminUniqueness(EducationalOrganization educationalOrganization) {
        if (userRepository.existsByRoleCodeAndEducationalOrganizationId(RoleCode.OO_ADMIN, educationalOrganization.getId())) {
            throw new AdministratorAlreadyExistsException("Для данной образовательной организации уже существует администратор.");
        }
    }

    private Role getRole(RoleCode roleCode) {
        return roleRepository.findByCode(roleCode)
                .orElseThrow(() -> new RoleNotFoundException("Роль не найдена: " + roleCode));
    }

    private Msu getMsu(Long msuId) {
        return msuRepository.findById(msuId)
                .orElseThrow(() -> new MsuNotFoundException("МСУ не найдено: " + msuId));
    }

    private EducationalOrganization getEducationalOrganization(Long educationalOrganizationId, Long msuId) {
        EducationalOrganization educationalOrganization = educationalOrganizationRepository
                        .findById(educationalOrganizationId)
                        .orElseThrow(() -> new EducationalOrganizationNotFoundException("Образовательная организация не найдена: " + educationalOrganizationId));

        if (!educationalOrganization.getMsu().getId().equals(msuId)) {
            throw new InvalidRegistrationScopeException("Образовательная организация не принадлежит указанному МСУ.");
        }
        return educationalOrganization;
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
