package org.donriro.nastavnik.profile.field.service;

import lombok.RequiredArgsConstructor;
import org.donriro.nastavnik.profile.field.dto.CreateProfileFieldRequest;
import org.donriro.nastavnik.profile.field.dto.ProfileFieldResponse;
import org.donriro.nastavnik.profile.field.dto.ProfileFieldRoleRequest;
import org.donriro.nastavnik.profile.field.dto.UpdateProfileFieldRequest;
import org.donriro.nastavnik.profile.field.entity.ProfileField;
import org.donriro.nastavnik.profile.field.entity.ProfileFieldRole;
import org.donriro.nastavnik.profile.field.entity.ProfileFieldRoleId;
import org.donriro.nastavnik.profile.field.exception.StaleProfileFieldException;
import org.donriro.nastavnik.profile.field.repository.ProfileFieldRepository;
import org.donriro.nastavnik.profile.field.repository.ProfileFieldRoleRepository;
import org.donriro.nastavnik.user.entity.Role;
import org.donriro.nastavnik.user.repository.RoleRepository;
import org.donriro.nastavnik.user.role.RoleCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProfileFieldService {

    private static final Set<RoleCode> ALLOWED_ROLES = EnumSet.of(RoleCode.MENTOR, RoleCode.MENTEE);

    private final ProfileFieldRepository profileFieldRepository;
    private final ProfileFieldRoleRepository profileFieldRoleRepository;
    private final RoleRepository roleRepository;

    @Transactional
    public ProfileFieldResponse create(CreateProfileFieldRequest request) {
        validateRequest(request);

        if (profileFieldRepository.existsByNameIgnoreCase(request.name().trim())) {
            throw new IllegalArgumentException("Поле с таким названием уже существует.");
        }

        ProfileField field = new ProfileField();
        applyRequest(field, request);

        field.setActive(true);
        field.setMatchingEnabled(false);

        ProfileField savedField = profileFieldRepository.save(field);

        replaceRoles(savedField, request.roles());

        savedField.setMatchingEnabled(request.matchingEnabled());

        return toResponse(savedField);
    }

    @Transactional
    public ProfileFieldResponse update(Long fieldId, UpdateProfileFieldRequest request) {
        ProfileField field = profileFieldRepository.findById(fieldId)
                .orElseThrow(() -> new IllegalArgumentException("Поле профиля не найдено: " + fieldId));

        if (!field.isActive()) {
            throw new IllegalStateException("Нельзя редактировать деактивированное поле.");
        }

        if (!field.getVersion().equals(request.expectedVersion())) {
            throw new StaleProfileFieldException(fieldId, request.expectedVersion(), field.getVersion());
        }

        validateUpdateRequest(request);

        if (profileFieldRepository.existsByNameIgnoreCaseAndIdNot(request.name().trim(), fieldId)) {
            throw new IllegalArgumentException("Поле с таким названием уже существует.");
        }

        field.setName(request.name().trim());
        field.setFieldType(request.fieldType());
        field.setSearchable(request.searchable());

        replaceRoles(field, request.roles());

        boolean assignedToBoth =
                request.roles().stream()
                        .map(ProfileFieldRoleRequest::roleCode)
                        .collect(Collectors.toSet())
                        .containsAll(Set.of(RoleCode.MENTOR, RoleCode.MENTEE));

        field.setMatchingEnabled(
                request.matchingEnabled() && assignedToBoth
        );

        return toResponse(field);
    }

    @Transactional
    public void deactivate(Long fieldId) {
        ProfileField field = profileFieldRepository.findById(fieldId)
                .orElseThrow(() -> new IllegalArgumentException("Поле профиля не найдено: " + fieldId));

        field.setActive(false);
        field.setMatchingEnabled(false);
    }

    private void validateUpdateRequest(UpdateProfileFieldRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Данные поля не должны быть null.");
        }

        if (request.expectedVersion() == null || request.expectedVersion() < 0) {
            throw new IllegalArgumentException("Необходимо указать корректную версию поля.");
        }

        if (request.name() == null || request.name().isBlank()) {
            throw new IllegalArgumentException("Название поля не должно быть пустым.");
        }

        if (request.name().length() > 255) {
            throw new IllegalArgumentException("Название поля не должно превышать 255 символов.");
        }

        if (request.fieldType() == null) {
            throw new IllegalArgumentException("Необходимо указать тип поля.");
        }

        if (request.roles() == null || request.roles().isEmpty() || request.roles().size() > 2) {
            throw new IllegalArgumentException("Необходимо указать одну или две роли.");
        }

        Set<RoleCode> roleCodes = request.roles()
                .stream()
                .map(role -> {
                    if (role == null || role.roleCode() == null) {
                        throw new IllegalArgumentException("Роль не должна быть null.");
                    }
                    if (role.sortOrder() < 0) {
                        throw new IllegalArgumentException("Порядок отображения не может быть отрицательным.");
                    }
                    return role.roleCode();
                })
                .collect(Collectors.toSet());

        if (roleCodes.size() != request.roles().size()) {
            throw new IllegalArgumentException("Одна и та же роль указана несколько раз.");
        }

        if (!ALLOWED_ROLES.containsAll(roleCodes)) {
            throw new IllegalArgumentException("Поле можно назначать только наставнику и наставляемому.");
        }

        if (request.matchingEnabled() && !roleCodes.containsAll(Set.of(RoleCode.MENTOR, RoleCode.MENTEE))) {
            throw new IllegalArgumentException("Для участия в подборе поле должно быть назначено обеим ролям.");
        }
    }

    private void applyRequest(ProfileField field, CreateProfileFieldRequest request) {
        field.setName(request.name().trim());
        field.setFieldType(request.fieldType());
        field.setSearchable(request.searchable());
    }

    private void validateRequest(CreateProfileFieldRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Данные поля не должны быть null.");
        }

        if (request.roles() == null || request.roles().isEmpty()) {
            throw new IllegalArgumentException("Необходимо назначить хотя бы одну роль.");
        }

        Set<RoleCode> roleCodes = request.roles().stream()
                .map(role -> {
                    if (role == null || role.roleCode() == null) {
                        throw new IllegalArgumentException("Роль не должна быть null.");
                    }
                    return role.roleCode();
                })
                .collect(Collectors.toSet());

        if (roleCodes.size() != request.roles().size()) {
            throw new IllegalArgumentException("Одна и та же роль указана несколько раз.");
        }

        if (!ALLOWED_ROLES.containsAll(roleCodes)) {
            throw new IllegalArgumentException("Поле можно назначать только наставнику и наставляемому.");
        }

        boolean assignedToBothRoles = roleCodes.contains(RoleCode.MENTOR) && roleCodes.contains(RoleCode.MENTEE);

        if (request.matchingEnabled() && !assignedToBothRoles) {
            throw new IllegalArgumentException("Для участия в подборе поле должно быть назначено и наставнику, и наставляемому.");
        }

        for (var roleRequest : request.roles()) {
            if (roleRequest.sortOrder() < 0) {
                throw new IllegalArgumentException("Порядок отображения не может быть отрицательным.");
            }
        }
    }

    private void replaceRoles(ProfileField field, List<ProfileFieldRoleRequest> roleRequests) {
        // Текущие назначения поля, индексированные по коду роли.
        Map<RoleCode, ProfileFieldRole> existingByRole = profileFieldRoleRepository
                        .findAllByProfileField_Id(field.getId())
                        .stream()
                        .collect(Collectors.toMap(assignment -> assignment.getRole().getCode(), Function.identity()));

        // Назначения, которые должны остаться после обновления.
        Set<RoleCode> requestedRoleCodes = roleRequests.stream()
                .map(ProfileFieldRoleRequest::roleCode)
                .collect(Collectors.toSet());

        // Удаляем только назначения, которых больше нет в запросе.
        List<ProfileFieldRole> assignmentsToDelete =
                existingByRole.entrySet().stream()
                        .filter(entry -> !requestedRoleCodes.contains(entry.getKey()))
                        .map(Map.Entry::getValue)
                        .toList();

        if (!assignmentsToDelete.isEmpty()) {
            profileFieldRoleRepository.deleteAll(assignmentsToDelete);
        }

        // Создаём новые назначения и обновляем существующие.
        for (ProfileFieldRoleRequest roleRequest : roleRequests) {
            RoleCode roleCode = roleRequest.roleCode();

            ProfileFieldRole assignment = existingByRole.get(roleCode);

            if (assignment == null) {
                Role role = roleRepository.findByCode(roleCode)
                        .orElseThrow(() -> new IllegalStateException("Роль не найдена в БД: " + roleCode));

                assignment = new ProfileFieldRole();

                ProfileFieldRoleId id = new ProfileFieldRoleId();
                id.setProfileFieldId(field.getId());
                id.setRoleId(role.getId());

                assignment.setId(id);
                assignment.setProfileField(field);
                assignment.setRole(role);
            }

            // Для существующего назначения меняем только настройки.
            assignment.setRequired(roleRequest.required());
            assignment.setSortOrder(roleRequest.sortOrder());

            profileFieldRoleRepository.save(assignment);
        }

        // Если поле перестало быть назначенным обеим ролям,
        // оно больше не может участвовать в подборе.
        boolean assignedToMentor =
                requestedRoleCodes.contains(RoleCode.MENTOR);

        boolean assignedToMentee =
                requestedRoleCodes.contains(RoleCode.MENTEE);

        if (!assignedToMentor || !assignedToMentee) {
            field.setMatchingEnabled(false);
        }
    }

    private ProfileFieldResponse toResponse(ProfileField field) {
        List<ProfileFieldResponse.ProfileFieldRoleResponse> roles =
                profileFieldRoleRepository
                        .findAllByProfileField_Id(field.getId())
                        .stream()
                        .sorted(Comparator.comparingInt(ProfileFieldRole::getSortOrder))
                        .map(role -> new ProfileFieldResponse
                                .ProfileFieldRoleResponse(
                                role.getRole().getCode(),
                                role.isRequired(),
                                role.getSortOrder()
                        ))
                        .toList();

        return new ProfileFieldResponse(
                field.getId(),
                field.getVersion(),
                field.getName(),
                field.getFieldType(),
                field.isSearchable(),
                field.isActive(),
                field.isMatchingEnabled(),
                roles
        );
    }
}
