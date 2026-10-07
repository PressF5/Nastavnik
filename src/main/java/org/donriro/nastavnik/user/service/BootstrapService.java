package org.donriro.nastavnik.user.service;

import lombok.RequiredArgsConstructor;
import org.donriro.nastavnik.organization.region.entity.Region;
import org.donriro.nastavnik.organization.region.repository.RegionRepository;
import org.donriro.nastavnik.user.dto.request.RegionAdminBootstrapRequest;
import org.donriro.nastavnik.user.dto.response.BootstrapStatusResponse;
import org.donriro.nastavnik.user.entity.Role;
import org.donriro.nastavnik.user.entity.User;
import org.donriro.nastavnik.user.exception.EmailAlreadyExistsException;
import org.donriro.nastavnik.user.exception.RegionAlreadyExistsException;
import org.donriro.nastavnik.user.exception.RegionAlreadyInitializedException;
import org.donriro.nastavnik.user.exception.RoleNotFoundException;
import org.donriro.nastavnik.user.repository.RoleRepository;
import org.donriro.nastavnik.user.repository.UserRepository;
import org.donriro.nastavnik.user.role.RoleCode;
import org.donriro.nastavnik.user.status.AccountStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BootstrapService {

    private final UserRepository userRepository;
    private final RegionRepository regionRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    public BootstrapStatusResponse getStatus() {
        boolean initialized = userRepository.existsByRoleCode(RoleCode.REGION_ADMIN);
        return new BootstrapStatusResponse(initialized);
    }

    @Transactional
    public User createRegionAdmin(RegionAdminBootstrapRequest request) {
        checkNotInitialized();

        String normalizedEmail = normalizeEmail(request.email());
        checkEmailUniqueness(normalizedEmail);

        Region region = createRegion(request);

        Role role = getRegionAdminRole();
        User user = buildRegionAdmin(request, normalizedEmail, region, role);

        return userRepository.save(user);
    }

    private void checkNotInitialized() {
        if (userRepository.existsByRoleCode(RoleCode.REGION_ADMIN)) {
            throw new RegionAlreadyInitializedException("Региональный администратор уже зарегистрирован.");
        }
    }

    private void checkEmailUniqueness(String email) {
        if (userRepository.existsByEmail(email)) {
            throw new EmailAlreadyExistsException("Пользователь с таким email уже зарегистрирован.");
        }
    }

    private Region createRegion(RegionAdminBootstrapRequest request) {
        if (regionRepository.existsByCode(request.regionCode())) {
            throw new RegionAlreadyExistsException("Регион с таким кодом уже существует.");
        }

        Region region = new Region();
        region.setCode(request.regionCode());
        region.setName(request.regionName().trim());

        return regionRepository.save(region);
    }

    private Role getRegionAdminRole() {
        return roleRepository.findByCode(RoleCode.REGION_ADMIN).orElseThrow(() -> new RoleNotFoundException("Роль REGION_ADMIN не найдена."));
    }

    private User buildRegionAdmin(RegionAdminBootstrapRequest request, String email, Region region, Role role) {
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
        user.setMsu(null);
        user.setEducationalOrganization(null);
        user.setRole(role);
        user.setAccountStatus(AccountStatus.ACCEPTED);
        user.setBlocked(false);

        return user;
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}