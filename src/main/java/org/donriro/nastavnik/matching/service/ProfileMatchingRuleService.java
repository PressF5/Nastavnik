package org.donriro.nastavnik.matching.service;

import lombok.RequiredArgsConstructor;
import org.donriro.nastavnik.matching.dto.CreateProfileMatchingRuleRequest;
import org.donriro.nastavnik.matching.dto.ProfileMatchingRuleResponse;
import org.donriro.nastavnik.matching.dto.UpdateProfileMatchingRuleRequest;
import org.donriro.nastavnik.matching.entity.ProfileMatchingRule;
import org.donriro.nastavnik.matching.exception.InvalidProfileMatchingRuleException;
import org.donriro.nastavnik.matching.exception.ProfileMatchingRuleAlreadyExistsException;
import org.donriro.nastavnik.matching.exception.ProfileMatchingRuleNotFoundException;
import org.donriro.nastavnik.matching.repository.ProfileMatchingRuleRepository;
import org.donriro.nastavnik.matching.type.ComparisonType;
import org.donriro.nastavnik.profile.field.entity.ProfileField;
import org.donriro.nastavnik.profile.field.repository.ProfileFieldRepository;
import org.donriro.nastavnik.profile.field.repository.ProfileFieldRoleRepository;
import org.donriro.nastavnik.profile.field.type.ProfileFieldType;
import org.donriro.nastavnik.user.role.RoleCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProfileMatchingRuleService {

    private final ProfileMatchingRuleRepository ruleRepository;
    private final ProfileFieldRepository profileFieldRepository;
    private final ProfileFieldRoleRepository profileFieldRoleRepository;

    @Transactional
    public ProfileMatchingRuleResponse create(CreateProfileMatchingRuleRequest request) {
        if (request == null) {
            throw new InvalidProfileMatchingRuleException("Данные правила не должны быть null.");
        }

        ProfileField field = profileFieldRepository
                .findById(request.profileFieldId())
                .orElseThrow(() -> new InvalidProfileMatchingRuleException("Поле анкеты с id=" + request.profileFieldId() + " не найдено."));

        validateField(field, request.comparisonType());

        if (ruleRepository.existsByProfileField_Id(field.getId())) {
            throw new ProfileMatchingRuleAlreadyExistsException("Для поля анкеты с id=" + field.getId() + " правило уже существует.");
        }

        ProfileMatchingRule rule = new ProfileMatchingRule();
        rule.setProfileField(field);
        rule.setComparisonType(request.comparisonType());
        rule.setWeight(request.weight());
        rule.setActive(true);

        return toResponse(ruleRepository.save(rule));
    }


    @Transactional
    public ProfileMatchingRuleResponse update(Long id, UpdateProfileMatchingRuleRequest request) {
        ProfileMatchingRule rule = ruleRepository.findById(id)
                .orElseThrow(() -> new ProfileMatchingRuleNotFoundException("Правило подбора с id " + id + " не найдено"));

        ProfileField field = rule.getProfileField();

        // При включении правила проверяем, можно ли использовать поле в подборе.
        if (request.active()) {
            validateField(field, request.comparisonType());
        } else {
            // При отключении проверяем совместимость типа только тогда,
            // когда правило продолжает быть активным.
            if (rule.isActive()) {
                validateComparisonType(field, request.comparisonType());
            }
        }

        rule.setComparisonType(request.comparisonType());
        rule.setWeight(request.weight());
        rule.setActive(request.active());

        return toResponse(ruleRepository.save(rule));
    }


    public ProfileMatchingRuleResponse getById(Long ruleId) {
        ProfileMatchingRule rule = ruleRepository.findById(ruleId)
                .orElseThrow(() -> new ProfileMatchingRuleNotFoundException("Правило сопоставления с id=" + ruleId + " не найдено."));

        return toResponse(rule);
    }

    public List<ProfileMatchingRuleResponse> getAll() {
        return ruleRepository.findAll()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public List<ProfileMatchingRuleResponse> getAllActive() {
        return ruleRepository.findAllByActiveTrue()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public void deactivate(Long ruleId) {
        ProfileMatchingRule rule = ruleRepository.findById(ruleId)
                .orElseThrow(() -> new ProfileMatchingRuleNotFoundException("Правило сопоставления с id=" + ruleId + " не найдено."));
        rule.setActive(false);
    }

    private void validateField(ProfileField field, ComparisonType comparisonType) {
        if (comparisonType == null) {
            throw new InvalidProfileMatchingRuleException("Необходимо указать тип сравнения.");
        }

        if (!field.isActive()) {
            throw new InvalidProfileMatchingRuleException("Нельзя создать или изменить правило для неактивного поля.");
        }

        if (!field.isMatchingEnabled()) {
            throw new InvalidProfileMatchingRuleException("Поле не включено в подбор наставника.");
        }

        boolean assignedToMentor = profileFieldRoleRepository.existsByProfileField_IdAndRole_Code(field.getId(), RoleCode.MENTOR);
        boolean assignedToMentee = profileFieldRoleRepository.existsByProfileField_IdAndRole_Code(field.getId(), RoleCode.MENTEE);

        if (!assignedToMentor || !assignedToMentee) {
            throw new InvalidProfileMatchingRuleException("Поле должно быть назначено и наставнику, и наставляемому.");
        }

        if (!isCompatible(field.getFieldType(), comparisonType)) {
            throw new InvalidProfileMatchingRuleException("Тип сравнения " + comparisonType + " несовместим с типом поля " + field.getFieldType() + ".");
        }
    }

    private boolean isCompatible(ProfileFieldType fieldType, ComparisonType comparisonType) {
        return switch (comparisonType) {
            case EXACT_MATCH -> fieldType == ProfileFieldType.TEXT || fieldType == ProfileFieldType.SELECT;
            case MULTISELECT_OVERLAP -> fieldType == ProfileFieldType.MULTISELECT;
            case TEXT_WORD_OVERLAP -> fieldType == ProfileFieldType.TEXT || fieldType == ProfileFieldType.TEXTAREA;
            case NUMERIC_PROXIMITY -> fieldType == ProfileFieldType.NUMBER;
            case BOOLEAN_MATCH -> fieldType == ProfileFieldType.BOOLEAN;
        };
    }

    private ProfileMatchingRuleResponse toResponse(ProfileMatchingRule rule) {
        ProfileField field = rule.getProfileField();

        return new ProfileMatchingRuleResponse(
                rule.getId(),
                field.getId(),
                field.getName(),
                rule.getComparisonType(),
                rule.getWeight(),
                rule.isActive()
        );
    }
}
