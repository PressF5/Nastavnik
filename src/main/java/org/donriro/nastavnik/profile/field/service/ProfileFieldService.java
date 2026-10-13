package org.donriro.nastavnik.profile.field.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.donriro.nastavnik.profile.field.dto.*;
import org.donriro.nastavnik.profile.field.entity.ProfileField;
import org.donriro.nastavnik.profile.field.entity.ProfileFieldRole;
import org.donriro.nastavnik.profile.field.entity.ProfileFieldRoleId;
import org.donriro.nastavnik.profile.field.exception.*;
import org.donriro.nastavnik.profile.field.repository.ProfileFieldRepository;
import org.donriro.nastavnik.profile.field.repository.ProfileFieldRoleRepository;
import org.donriro.nastavnik.profile.field.type.ProfileFieldType;
import org.donriro.nastavnik.user.entity.Role;
import org.donriro.nastavnik.user.repository.RoleRepository;
import org.donriro.nastavnik.user.role.RoleCode;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProfileFieldService {

    private static final Set<RoleCode> ALLOWED_ROLES = EnumSet.of(RoleCode.MENTOR, RoleCode.MENTEE);

    private final ProfileFieldRepository profileFieldRepository;
    private final ProfileFieldRoleRepository profileFieldRoleRepository;
    private final RoleRepository roleRepository;
    private final ProfileFieldOptionService profileFieldOptionService;

    private final EntityManager entityManager;

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

        // Для SELECT и MULTISELECT создаём варианты в той же транзакции.
        if (isChoiceField(savedField.getFieldType())) {
            profileFieldOptionService.createAllForField(savedField, request.options());
        }

        savedField.setMatchingEnabled(request.matchingEnabled());

        return toResponse(savedField);
    }

    @Transactional
    public ProfileFieldResponse update(Long fieldId, UpdateProfileFieldRequest request) {
        // Сначала проверяем запрос, чтобы исключить NullPointerException.
        validateUpdateRequest(request);

        ProfileField field = profileFieldRepository.findById(fieldId)
                .orElseThrow(() -> new ProfileFieldNotFoundException("Поле профиля с id=" + fieldId + " не найдено."));

        if (!field.isActive()) {
            throw new InactiveProfileFieldException("Нельзя редактировать деактивированное поле.");
        }

        // Защита от устаревшей формы.
        if (!field.getVersion().equals(request.expectedVersion())) {
            throw new StaleProfileFieldException(fieldId, request.expectedVersion(), field.getVersion());
        }

        if (profileFieldRepository.existsByNameIgnoreCaseAndIdNot(request.name().trim(), fieldId)) {
            throw new IllegalArgumentException("Поле с таким названием уже существует.");
        }

        ProfileFieldType currentType = field.getFieldType();
        ProfileFieldType requestedType = request.fieldType();

        boolean currentIsChoice = isChoiceField(currentType);
        boolean requestedIsChoice = isChoiceField(requestedType);

        if (currentType != requestedType) {
            boolean hasOptions =
                    profileFieldOptionService.hasOptions(field.getId());

            if (isChoiceField(currentType) && !isChoiceField(requestedType) && hasOptions) {
                throw new ProfileFieldTypeChangeNotAllowedException("Нельзя изменить тип поля, пока у него существуют варианты.");
            }

            if (!isChoiceField(currentType) && isChoiceField(requestedType)) {
                throw new ProfileFieldOptionsRequiredException(
                        "Для перехода к SELECT или MULTISELECT необходимо предусмотреть передачу вариантов поля."
                );
            }
        }

        field.setName(request.name().trim());
        field.setFieldType(request.fieldType());
        field.setSearchable(request.searchable());

        replaceRoles(field, request.roles());

        boolean assignedToBoth = request.roles().stream()
                .map(ProfileFieldRoleRequest::roleCode)
                .collect(Collectors.toSet())
                .containsAll(Set.of(RoleCode.MENTOR, RoleCode.MENTEE));

        field.setMatchingEnabled(request.matchingEnabled() && assignedToBoth);

        /*
         * Назначения profile_field_role тоже должны учитываться
         * при конкурентном редактировании.
         *
         * OPTIMISTIC_FORCE_INCREMENT запрашивает увеличение версии
         * родительской сущности даже тогда, когда её обычные поля
         * не изменились, а изменились только назначения ролей.
         */
        entityManager.lock(field, LockModeType.OPTIMISTIC_FORCE_INCREMENT);

        // Выполняем SQL до формирования ответа.
        entityManager.flush();

        // Возвращаем актуальную версию после синхронизации.
        entityManager.refresh(field);

        return toResponse(field);
    }

    @Transactional
    public void deactivate(Long fieldId) {
        ProfileField field = profileFieldRepository.findById(fieldId)
                .orElseThrow(() -> new ProfileFieldNotFoundException("Поле профиля с id=" + fieldId + " не найдено."));

        field.setActive(false);
        field.setMatchingEnabled(false);
    }

    @Transactional
    public void activate(Long id) {
        ProfileField field = profileFieldRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Поле анкеты с ID " + id + " не найдено."));

        if (field.isActive()) {
            return;
        }

        field.setActive(true);
    }

    public ProfileFieldResponse getById(Long id) {
        ProfileField field = profileFieldRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Поле анкеты с ID " + id + " не найдено."));
        return toResponse(field);
    }

    public List<ProfileFieldResponse> getAll() {
        return profileFieldRepository.findAll()
                .stream()
                .map(this::toResponse)
                .toList();
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

        validateOptions(request.fieldType(), request.options());
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
        boolean assignedToMentor = requestedRoleCodes.contains(RoleCode.MENTOR);
        boolean assignedToMentee = requestedRoleCodes.contains(RoleCode.MENTEE);

        if (!assignedToMentor || !assignedToMentee) {
            field.setMatchingEnabled(false);
        }
    }


    private void validateOptions(ProfileFieldType fieldType, List<ProfileFieldOptionRequest> options) {
        boolean choiceField = isChoiceField(fieldType);

        // Для обычных полей варианты не допускаются.
        if (!choiceField) {
            if (options != null && !options.isEmpty()) {
                throw new IllegalArgumentException("Варианты можно указывать только для полей типа SELECT или MULTISELECT.");
            }
            return;
        }

        // Для SELECT и MULTISELECT нужен хотя бы один вариант.
        if (options == null || options.isEmpty()) {
            throw new IllegalArgumentException("Для поля типа SELECT или MULTISELECT необходимо указать хотя бы один вариант.");
        }

        Set<String> values = new HashSet<>();

        for (ProfileFieldOptionRequest option : options) {
            if (option == null) {
                throw new IllegalArgumentException("Вариант поля не должен быть null.");
            }

            if (option.value() == null || option.value().isBlank() || option.value().length() > 255) {
                throw new IllegalArgumentException("value варианта должен содержать от 1 до 255 символов.");
            }

            if (option.label() == null || option.label().isBlank() || option.label().length() > 255) {
                throw new IllegalArgumentException("label варианта должен содержать от 1 до 255 символов.");
            }

            if (option.sortOrder() < 0) {
                throw new IllegalArgumentException("Порядок отображения варианта не может быть отрицательным.");
            }

            // Значения сравниваем после trim().
            String normalizedValue = option.value().trim();

            if (!values.add(normalizedValue)) {
                throw new IllegalArgumentException("Варианты одного поля не должны содержать повторяющиеся value: " + normalizedValue);
            }
        }
    }

    private boolean isChoiceField(ProfileFieldType fieldType) {
        return fieldType == ProfileFieldType.SELECT || fieldType == ProfileFieldType.MULTISELECT;
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

        List<ProfileFieldOptionResponse> options = isChoiceField(field.getFieldType()) ? profileFieldOptionService.getAll(field.getId()) : List.of();

        return new ProfileFieldResponse(
                field.getId(),
                field.getVersion(),
                field.getName(),
                field.getFieldType(),
                field.isSearchable(),
                field.isActive(),
                field.isMatchingEnabled(),
                roles,
                options
        );
    }
}
