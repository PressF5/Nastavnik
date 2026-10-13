package org.donriro.nastavnik.profile.value.service;

import lombok.RequiredArgsConstructor;
import org.donriro.nastavnik.profile.field.entity.ProfileField;
import org.donriro.nastavnik.profile.field.entity.ProfileFieldOption;
import org.donriro.nastavnik.profile.field.entity.ProfileFieldRole;
import org.donriro.nastavnik.profile.field.repository.ProfileFieldOptionRepository;
import org.donriro.nastavnik.profile.field.repository.ProfileFieldRepository;
import org.donriro.nastavnik.profile.field.repository.ProfileFieldRoleRepository;
import org.donriro.nastavnik.profile.field.type.ProfileFieldType;
import org.donriro.nastavnik.profile.value.dto.ProfileValueResponse;
import org.donriro.nastavnik.profile.value.dto.SaveProfileValueRequest;
import org.donriro.nastavnik.profile.value.entity.ProfileValue;
import org.donriro.nastavnik.profile.value.entity.ProfileValueOption;
import org.donriro.nastavnik.profile.value.entity.ProfileValueOptionId;
import org.donriro.nastavnik.profile.value.repository.ProfileValueOptionRepository;
import org.donriro.nastavnik.profile.value.repository.ProfileValueRepository;
import org.donriro.nastavnik.security.principal.CustomUserDetails;
import org.donriro.nastavnik.user.entity.User;
import org.donriro.nastavnik.user.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProfileValueService {

    private final ProfileValueRepository profileValueRepository;
    private final ProfileValueOptionRepository profileValueOptionRepository;
    private final ProfileFieldRepository profileFieldRepository;
    private final ProfileFieldRoleRepository profileFieldRoleRepository;
    private final ProfileFieldOptionRepository profileFieldOptionRepository;
    private final UserRepository userRepository;

    @Transactional
    public ProfileValueResponse saveForCurrentUser(SaveProfileValueRequest request) {
        if (request == null || request.profileFieldId() == null) {
            throw badRequest("Необходимо указать поле анкеты.");
        }

        User user = getCurrentUserFromDatabase();

        ProfileField field = profileFieldRepository.findById(request.profileFieldId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Поле анкеты не найдено."));

        if (!field.isActive()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Нельзя заполнять неактивное поле анкеты.");
        }

        ProfileFieldRole assignment = profileFieldRoleRepository
                .findAllByProfileField_Id(field.getId())
                .stream()
                .filter(item -> item.getRole().getCode() == user.getRole().getCode())
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.FORBIDDEN,
                        "Это поле не назначено вашей роли."));

        validateValue(request, field.getFieldType(), assignment.isRequired());

        List<ProfileFieldOption> selectedOptions =
                resolveOptions(request.optionIds(), field);

        ProfileValue value = profileValueRepository
                .findByUser_IdAndProfileField_Id(user.getId(), field.getId())
                .orElseGet(ProfileValue::new);

        value.setUser(user);
        value.setProfileField(field);
        value.setValueText(normalizeText(request.valueText()));
        value.setValueNumber(request.valueNumber());
        value.setValueBoolean(request.valueBoolean());
        value.setValueDate(request.valueDate());

        value = profileValueRepository.saveAndFlush(value);

        // Сначала удаляем прежний выбор, затем записываем новый.
        // Всё выполняется в одной транзакции.
        profileValueOptionRepository.deleteAllByProfileValue_Id(value.getId());
        profileValueOptionRepository.flush();

        List<ProfileValueOption> links = new ArrayList<>();

        for (ProfileFieldOption option : selectedOptions) {
            ProfileValueOption link = new ProfileValueOption();

            ProfileValueOptionId id = new ProfileValueOptionId();
            id.setProfileValueId(value.getId());
            id.setProfileOptionId(option.getId());

            link.setId(id);
            link.setProfileValue(value);
            link.setProfileOption(option);
            link.setProfileField(field);

            links.add(link);
        }

        if (!links.isEmpty()) {
            profileValueOptionRepository.saveAll(links);
            profileValueOptionRepository.flush();
        }

        return toResponse(value, selectedOptions);
    }

    public List<ProfileValueResponse> getAllForCurrentUser() {
        User user = getCurrentUserFromDatabase();

        return profileValueRepository.findAllByUser_Id(user.getId())
                .stream()
                .map(value -> {
                    List<ProfileFieldOption> selectedOptions =
                            profileValueOptionRepository
                                    .findAllByProfileValue_Id(value.getId())
                                    .stream()
                                    .map(ProfileValueOption::getProfileOption)
                                    .toList();

                    return toResponse(value, selectedOptions);
                })
                .toList();
    }

    private User getCurrentUserFromDatabase() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null
                || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof CustomUserDetails details)) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "Необходимо войти в систему.");
        }

        Long userId = details.getUser().getId();

        return userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Текущий пользователь не найден."));
    }

    private void validateValue(SaveProfileValueRequest request, ProfileFieldType fieldType, boolean required) {
        boolean hasText = request.valueText() != null && !request.valueText().isBlank();
        boolean hasNumber = request.valueNumber() != null;
        boolean hasBoolean = request.valueBoolean() != null;
        boolean hasDate = request.valueDate() != null;
        boolean hasOptions = request.optionIds() != null && !request.optionIds().isEmpty();

        switch (fieldType) {
            case TEXT, TEXTAREA -> {
                if (hasNumber || hasBoolean || hasDate || hasOptions) {
                    throw badRequest("Для текстового поля передано значение неподходящего типа.");
                }
                if (required && !hasText) {
                    throw badRequest("Это поле обязательно для заполнения.");
                }
            }
            case NUMBER -> {
                if (hasText || hasBoolean || hasDate || hasOptions) {
                    throw badRequest("Для числового поля передано значение неподходящего типа.");
                }
                if (required && !hasNumber) {
                    throw badRequest("Это поле обязательно для заполнения.");
                }
            }
            case BOOLEAN -> {
                if (hasText || hasNumber || hasDate || hasOptions) {
                    throw badRequest("Для логического поля передано значение неподходящего типа.");
                }
                if (required && !hasBoolean) {
                    throw badRequest("Это поле обязательно для заполнения.");
                }
            }
            case DATE -> {
                if (hasText || hasNumber || hasBoolean || hasOptions) {
                    throw badRequest("Для поля даты передано значение неподходящего типа.");
                }
                if (required && !hasDate) {
                    throw badRequest("Это поле обязательно для заполнения.");
                }
            }
            case SELECT -> {
                if (hasText || hasNumber || hasBoolean || hasDate) {
                    throw badRequest("Для поля выбора нельзя передавать типизированное значение.");
                }
                int count = request.optionIds() == null ? 0 : request.optionIds().size();
                if (count > 1) {
                    throw badRequest("Для поля SELECT можно выбрать только один вариант.");
                }
                if (required && count != 1) {
                    throw badRequest("Необходимо выбрать один вариант.");
                }
            }
            case MULTISELECT -> {
                if (hasText || hasNumber || hasBoolean || hasDate) {
                    throw badRequest("Для поля множественного выбора нельзя передавать типизированное значение.");
                }
                if (required && !hasOptions) {
                    throw badRequest("Необходимо выбрать хотя бы один вариант.");
                }
            }
        }
    }

    private List<ProfileFieldOption> resolveOptions(List<Long> optionIds, ProfileField field) {
        boolean choiceField = field.getFieldType() == ProfileFieldType.SELECT || field.getFieldType() == ProfileFieldType.MULTISELECT;

        if (!choiceField) {
            return List.of();
        }

        if (optionIds == null || optionIds.isEmpty()) {
            return List.of();
        }

        Set<Long> uniqueIds = new HashSet<>(optionIds);
        if (uniqueIds.size() != optionIds.size()) {
            throw badRequest("Список выбранных вариантов содержит дубликаты.");
        }

        List<ProfileFieldOption> options = profileFieldOptionRepository.findAllById(uniqueIds);

        if (options.size() != uniqueIds.size()) {
            throw badRequest("Один или несколько вариантов не существуют.");
        }

        for (ProfileFieldOption option : options) {
            if (!option.isActive()) {
                throw badRequest("Нельзя выбрать неактивный вариант.");
            }

            if (!option.getProfileField().getId().equals(field.getId())) {
                throw badRequest("Выбранный вариант относится к другому полю.");
            }
        }

        // Стабильный порядок в ответе: sortOrder, затем id.
        options.sort(Comparator.comparingInt(ProfileFieldOption::getSortOrder)
                .thenComparingLong(ProfileFieldOption::getId));

        return options;
    }

    private String normalizeText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private ProfileValueResponse toResponse(ProfileValue value, List<ProfileFieldOption> selectedOptions) {
        List<ProfileValueResponse.SelectedOptionResponse> options =
                selectedOptions.stream()
                        .map(option -> new ProfileValueResponse.SelectedOptionResponse(
                                option.getId(),
                                option.getValue(),
                                option.getLabel()))
                        .toList();

        return new ProfileValueResponse(
                value.getId(),
                value.getProfileField().getId(),
                value.getProfileField().getName(),
                value.getProfileField().getFieldType(),
                value.getValueText(),
                value.getValueNumber(),
                value.getValueBoolean(),
                value.getValueDate(),
                options,
                value.getCreatedAt(),
                value.getUpdatedAt()
        );
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
