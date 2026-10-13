package org.donriro.nastavnik.profile.field.service;

import lombok.RequiredArgsConstructor;
import org.donriro.nastavnik.profile.field.dto.ProfileFieldOptionRequest;
import org.donriro.nastavnik.profile.field.dto.ProfileFieldOptionResponse;
import org.donriro.nastavnik.profile.field.entity.ProfileField;
import org.donriro.nastavnik.profile.field.entity.ProfileFieldOption;
import org.donriro.nastavnik.profile.field.repository.ProfileFieldOptionRepository;
import org.donriro.nastavnik.profile.field.repository.ProfileFieldRepository;
import org.donriro.nastavnik.profile.field.type.ProfileFieldType;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProfileFieldOptionService {

    private final ProfileFieldOptionRepository optionRepository;
    private final ProfileFieldRepository fieldRepository;


    public List<ProfileFieldOptionResponse> getAll(Long fieldId) {
        getField(fieldId);

        return optionRepository.findAllByProfileField_Id(fieldId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public ProfileFieldOptionResponse create(Long fieldId, ProfileFieldOptionRequest request) {
        ProfileField field = getField(fieldId);
        validateFieldType(field);

        if (optionRepository.existsByProfileField_IdAndValue(fieldId, request.value())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Вариант с таким value уже существует для этого поля.");
        }

        ProfileFieldOption option = new ProfileFieldOption();
        option.setProfileField(field);
        option.setValue(request.value());
        option.setLabel(request.label());

        return toResponse(optionRepository.save(option));
    }

    @Transactional
    public ProfileFieldOptionResponse update(Long fieldId, Long optionId, ProfileFieldOptionRequest request) {
        ProfileFieldOption option = getOption(fieldId, optionId);

        if (optionRepository.existsByProfileField_IdAndValueAndIdNot(fieldId, request.value(), optionId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Вариант с таким value уже существует для этого поля.");
        }

        option.setValue(request.value());
        option.setLabel(request.label());

        return toResponse(option);
    }

    @Transactional
    public void deactivate(Long fieldId, Long optionId) {
        ProfileFieldOption option = getOption(fieldId, optionId);
        option.setActive(false);
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

    private ProfileField getField(Long fieldId) {
        return fieldRepository.findById(fieldId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Поле анкеты с ID " + fieldId + " не найдено."));
    }

    private ProfileFieldOption getOption(Long fieldId, Long optionId) {
        return optionRepository.findAllByProfileField_Id(fieldId)
                .stream()
                .filter(option -> option.getId().equals(optionId))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Вариант не найден у указанного поля."));
    }

    private void validateFieldType(ProfileField field) {
        if (field.getFieldType() != ProfileFieldType.SELECT && field.getFieldType() != ProfileFieldType.MULTISELECT) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Варианты можно создавать только для SELECT и MULTISELECT.");
        }
    }

    private ProfileFieldOptionResponse toResponse(ProfileFieldOption option) {
        return new ProfileFieldOptionResponse(
                option.getId(),
                option.getProfileField().getId(),
                option.getValue(),
                option.getLabel(),
                option.isActive()
        );
    }
}
