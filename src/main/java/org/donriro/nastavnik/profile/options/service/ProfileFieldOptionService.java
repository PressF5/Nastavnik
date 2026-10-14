package org.donriro.nastavnik.profile.options.service;

import lombok.RequiredArgsConstructor;
import org.donriro.nastavnik.profile.options.dto.ProfileFieldOptionRequest;
import org.donriro.nastavnik.profile.options.dto.ProfileFieldOptionResponse;
import org.donriro.nastavnik.profile.field.entity.ProfileField;
import org.donriro.nastavnik.profile.options.entity.ProfileFieldOption;
import org.donriro.nastavnik.profile.options.repository.ProfileFieldOptionRepository;
import org.donriro.nastavnik.profile.field.repository.ProfileFieldRepository;
import org.donriro.nastavnik.profile.field.type.ProfileFieldType;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProfileFieldOptionService {

    private final ProfileFieldOptionRepository optionRepository;
    private final ProfileFieldRepository fieldRepository;

    public List<ProfileFieldOptionResponse> getAll(Long fieldId) {
        getField(fieldId);

        return optionRepository
                .findAllByProfileField_IdOrderBySortOrderAscIdAsc(fieldId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public ProfileFieldOptionResponse create(Long fieldId, ProfileFieldOptionRequest request) {
        ProfileField field = getField(fieldId);
        validateFieldType(field);
        validateOptionRequest(request);

        if (!field.isActive()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Нельзя добавлять варианты к неактивному полю.");
        }

        String value = request.value().trim();

        if (optionRepository.existsByProfileField_IdAndValue(fieldId, value)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Вариант с таким value уже существует.");
        }

        ProfileFieldOption option = new ProfileFieldOption();
        option.setProfileField(field);
        option.setValue(value);
        option.setLabel(request.label().trim());
        option.setSortOrder(request.sortOrder());

        return toResponse(optionRepository.save(option));
    }

    @Transactional
    public ProfileFieldOptionResponse update(Long fieldId, Long optionId, ProfileFieldOptionRequest request) {
        ProfileFieldOption option = getOption(fieldId, optionId);
        validateOptionRequest(request);
        validateFieldType(option.getProfileField());

        String value = request.value().trim();

        if (optionRepository.existsByProfileField_IdAndValueAndIdNot(fieldId, value, optionId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Вариант с таким value уже существует.");
        }

        option.setValue(value);
        option.setLabel(request.label().trim());
        option.setSortOrder(request.sortOrder());

        return toResponse(option);
    }

    @Transactional
    public void deactivate(Long fieldId, Long optionId) {
        ProfileFieldOption option = getOption(fieldId, optionId);
        option.setActive(false);
    }

    @Transactional
    public void activate(Long fieldId, Long optionId) {
        ProfileFieldOption option = getOption(fieldId, optionId);

        if (!option.getProfileField().isActive()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Нельзя активировать вариант неактивного поля.");
        }

        option.setActive(true);
    }


    /**
     * Создаёт все варианты поля в той же транзакции, что и само поле.
     * Вызывается при создании SELECT/MULTISELECT-поля.
     */
    @Transactional
    public void createAllForField(ProfileField field, List<ProfileFieldOptionRequest> requests) {
        validateFieldType(field);

        if (requests == null || requests.isEmpty()) {
            throw new IllegalArgumentException(
                    "Для поля типа SELECT или MULTISELECT необходимо указать хотя бы один вариант.");
        }

        Set<String> values = new HashSet<>();
        for (ProfileFieldOptionRequest request : requests) {
            validateOptionRequest(request);
            String normalizedValue = request.value().trim();
            if (!values.add(normalizedValue)) {
                throw new IllegalArgumentException(
                        "Варианты одного поля не должны содержать повторяющиеся value: " + normalizedValue);
            }
        }

        List<ProfileFieldOption> options = requests.stream()
                .map(request -> {
                    ProfileFieldOption option = new ProfileFieldOption();
                    option.setProfileField(field);
                    option.setValue(request.value().trim());
                    option.setLabel(request.label().trim());
                    option.setSortOrder(request.sortOrder());
                    return option;
                })
                .toList();

        optionRepository.saveAll(options);
    }

    public List<ProfileFieldOptionResponse> getActiveOptions(Long fieldId) {
        ProfileField field = getField(fieldId);

        if (!field.isActive()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Поле анкеты не найдено или неактивно.");
        }

        validateFieldType(field);

        return optionRepository
                .findAllByProfileField_IdAndActiveTrueOrderBySortOrderAscIdAsc(fieldId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public boolean hasOptions(Long fieldId) {
        return optionRepository.existsByProfileField_Id(fieldId);
    }

    private ProfileField getField(Long fieldId) {
        return fieldRepository.findById(fieldId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Поле анкеты не найдено: " + fieldId));
    }

    private ProfileFieldOption getOption(Long fieldId, Long optionId) {
        return optionRepository
                .findByIdAndProfileField_Id(optionId, fieldId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Вариант не найден: " + optionId));
    }

    private void validateFieldType(ProfileField field) {
        if (field.getFieldType() != ProfileFieldType.SELECT && field.getFieldType() != ProfileFieldType.MULTISELECT) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Варианты можно создавать только для SELECT и MULTISELECT.");
        }
    }

    private void validateOptionRequest(ProfileFieldOptionRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Вариант поля не должен быть null.");
        }
        if (request.value() == null || request.value().isBlank() || request.value().length() > 255) {
            throw new IllegalArgumentException("value варианта должен содержать от 1 до 255 символов.");
        }
        if (request.label() == null || request.label().isBlank() || request.label().length() > 255) {
            throw new IllegalArgumentException("label варианта должен содержать от 1 до 255 символов.");
        }
        if (request.sortOrder() < 0) {
            throw new IllegalArgumentException("Порядок отображения варианта не может быть отрицательным.");
        }
    }

    private ProfileFieldOptionResponse toResponse(ProfileFieldOption option) {
        return new ProfileFieldOptionResponse(
                option.getId(),
                option.getProfileField().getId(),
                option.getValue(),
                option.getLabel(),
                option.isActive(),
                option.getSortOrder()
        );
    }
}
