package org.donriro.nastavnik.matching.service;

import lombok.RequiredArgsConstructor;
import org.donriro.nastavnik.matching.dto.ProfileCompatibilityResponse;
import org.donriro.nastavnik.matching.entity.ProfileMatchingRule;
import org.donriro.nastavnik.matching.repository.ProfileMatchingRuleRepository;
import org.donriro.nastavnik.profile.field.entity.ProfileField;
import org.donriro.nastavnik.profile.field.type.ProfileFieldType;
import org.donriro.nastavnik.profile.value.entity.ProfileValue;
import org.donriro.nastavnik.profile.value.entity.ProfileValueOption;
import org.donriro.nastavnik.profile.value.repository.ProfileValueOptionRepository;
import org.donriro.nastavnik.profile.value.repository.ProfileValueRepository;
import org.donriro.nastavnik.user.entity.User;
import org.donriro.nastavnik.user.repository.UserRepository;
import org.donriro.nastavnik.user.role.RoleCode;
import org.donriro.nastavnik.user.status.AccountStatus;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProfileCompatibilityCalculator {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final BigDecimal ONE = BigDecimal.ONE;

    private static final Pattern NON_WORD_CHARACTERS = Pattern.compile("[^\\p{L}\\p{N}]+");

    private final UserRepository userRepository;
    private final ProfileValueRepository profileValueRepository;
    private final ProfileValueOptionRepository profileValueOptionRepository;
    private final ProfileMatchingRuleRepository profileMatchingRuleRepository;

    /**
     * Одиночный расчёт совместимости.
     */
    public ProfileCompatibilityResponse calculate(Long mentorId, Long menteeId) {
        return calculateForMentors(List.of(mentorId), menteeId).get(mentorId);
    }

    /**
     * Пакетный расчёт совместимости нескольких наставников
     * с одним наставляемым.
     */
    public Map<Long, ProfileCompatibilityResponse> calculateForMentors(Collection<Long> mentorIds, Long menteeId) {
        if (mentorIds == null || menteeId == null) {
            throw new IllegalArgumentException("Необходимо указать наставников и наставляемого.");
        }

        if (mentorIds.isEmpty()) {
            return Map.of();
        }

        Set<Long> uniqueMentorIds = new LinkedHashSet<>(mentorIds);

        if (uniqueMentorIds.contains(null) || uniqueMentorIds.contains(menteeId)) {
            throw new IllegalArgumentException("Необходимо указать корректные ID наставников.");
        }

        // Загружаем всех пользователей одним запросом.
        Set<Long> userIds = new HashSet<>(uniqueMentorIds);
        userIds.add(menteeId);

        Map<Long, User> users = userRepository.findAllById(userIds)
                .stream()
                .collect(Collectors.toMap(User::getId, user -> user));

        User mentee = users.get(menteeId);

        if (mentee == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Наставляемый не найден.");
        }

        for (Long mentorId : uniqueMentorIds) {
            User mentor = users.get(mentorId);

            if (mentor == null) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Наставник не найден.");
            }

            validateUsers(mentor, mentee);
        }

        // Загружаем значения профилей всех пользователей одним запросом.
        List<ProfileValue> profileValues = profileValueRepository.findAllByUser_IdIn(userIds);

        Map<Long, Map<Long, ProfileValue>> valuesByUserId = new HashMap<>();

        for (ProfileValue value : profileValues) {
            valuesByUserId
                    .computeIfAbsent(value.getUser().getId(), ignored -> new HashMap<>())
                    .put(value.getProfileField().getId(), value);
        }

        // Загружаем выбранные варианты всех значений одним запросом.
        Set<Long> profileValueIds = profileValues.stream()
                .map(ProfileValue::getId)
                .collect(Collectors.toSet());

        Map<Long, Set<Long>> selectedOptionsByValueId = new HashMap<>();

        if (!profileValueIds.isEmpty()) {
            List<ProfileValueOption> selectedOptions =
                    profileValueOptionRepository.findAllByProfileValue_IdIn(profileValueIds);

            for (ProfileValueOption option : selectedOptions) {
                selectedOptionsByValueId
                        .computeIfAbsent(option.getProfileValue().getId(), ignored -> new HashSet<>())
                        .add(option.getProfileOption().getId());
            }
        }

        // Правила сопоставления загружаем только один раз.
        List<ProfileMatchingRule> rules = profileMatchingRuleRepository.findAllByActiveTrue();

        Map<Long, ProfileCompatibilityResponse> results = new LinkedHashMap<>();

        Map<Long, ProfileValue> menteeValues = valuesByUserId.getOrDefault(menteeId, Map.of());

        for (Long mentorId : uniqueMentorIds) {
            Map<Long, ProfileValue> mentorValues = valuesByUserId.getOrDefault(mentorId, Map.of());

            ProfileCompatibilityResponse response = calculateCompatibility(
                    mentorId,
                    menteeId,
                    mentorValues,
                    menteeValues,
                    selectedOptionsByValueId,
                    rules
            );

            results.put(mentorId, response);
        }

        return results;
    }

    private ProfileCompatibilityResponse calculateCompatibility(
            Long mentorId,
            Long menteeId,
            Map<Long, ProfileValue> mentorValues,
            Map<Long, ProfileValue> menteeValues,
            Map<Long, Set<Long>> selectedOptionsByValueId,
            List<ProfileMatchingRule> rules
    ) {
        List<ProfileCompatibilityResponse.FieldCompatibility> details = new ArrayList<>();

        BigDecimal weightedScore = ZERO;
        BigDecimal totalWeight = ZERO;
        int skippedFieldCount = 0;

        for (ProfileMatchingRule rule : rules) {
            if (!isRuleEligible(rule)) {
                skippedFieldCount++;
                continue;
            }

            Long fieldId = rule.getProfileField().getId();

            ProfileValue mentorValue = mentorValues.get(fieldId);
            ProfileValue menteeValue = menteeValues.get(fieldId);

            if (!hasComparableValue(mentorValue, rule, selectedOptionsByValueId) ||
                    !hasComparableValue(menteeValue, rule, selectedOptionsByValueId)) {
                skippedFieldCount++;
                continue;
            }

            BigDecimal similarity = compare(mentorValue, menteeValue, rule, selectedOptionsByValueId);

            BigDecimal weight = rule.getWeight();

            weightedScore = weightedScore.add(similarity.multiply(weight));

            totalWeight = totalWeight.add(weight);

            details.add(new ProfileCompatibilityResponse.FieldCompatibility(
                    fieldId,
                    rule.getProfileField().getName(),
                    rule.getComparisonType().name(),
                    weight,
                    similarity.multiply(HUNDRED).setScale(2, RoundingMode.HALF_UP)
            ));
        }

        BigDecimal percentage = totalWeight.compareTo(ZERO) == 0
                ? null
                : weightedScore
                .divide(totalWeight, 6, RoundingMode.HALF_UP)
                .multiply(HUNDRED)
                .setScale(2, RoundingMode.HALF_UP);

        return new ProfileCompatibilityResponse(
                mentorId,
                menteeId,
                percentage,
                details.size(),
                skippedFieldCount,
                List.copyOf(details)
        );
    }

    private boolean hasComparableValue(ProfileValue value, ProfileMatchingRule rule, Map<Long, Set<Long>> selectedOptionsByValueId) {
        if (value == null) {
            return false;
        }

        ProfileFieldType fieldType = rule.getProfileField().getFieldType();

        return switch (fieldType) {
            case TEXT, TEXTAREA -> value.getValueText() != null && !value.getValueText().isBlank();
            case NUMBER -> value.getValueNumber() != null;
            case BOOLEAN -> value.getValueBoolean() != null;
            case SELECT, MULTISELECT -> !getSelectedOptionIds(value, selectedOptionsByValueId).isEmpty();
            case DATE -> false;
        };
    }

    private BigDecimal compare(ProfileValue mentor, ProfileValue mentee, ProfileMatchingRule rule, Map<Long, Set<Long>> selectedOptionsByValueId) {
        return switch (rule.getComparisonType()) {
            case EXACT_MATCH -> exactMatch(mentor, mentee, rule, selectedOptionsByValueId);
            case MULTISELECT_OVERLAP -> jaccardSimilarity(getSelectedOptionIds(mentor, selectedOptionsByValueId), getSelectedOptionIds(mentee, selectedOptionsByValueId));
            case TEXT_WORD_OVERLAP -> jaccardSimilarity(tokenize(mentor.getValueText()), tokenize(mentee.getValueText()));
            case NUMERIC_PROXIMITY -> numericProximity(mentor.getValueNumber(), mentee.getValueNumber());
            case BOOLEAN_MATCH -> mentor.getValueBoolean().equals(mentee.getValueBoolean()) ? ONE : ZERO;
        };
    }

    private BigDecimal exactMatch(
            ProfileValue mentor,
            ProfileValue mentee,
            ProfileMatchingRule rule,
            Map<Long, Set<Long>> selectedOptionsByValueId) {
        return switch (rule.getProfileField().getFieldType()) {
            case TEXT -> normalize(mentor.getValueText()).equals(normalize(mentee.getValueText())) ? ONE : ZERO;
            case SELECT -> getSelectedOptionIds(mentor, selectedOptionsByValueId)
                            .equals(getSelectedOptionIds(mentee, selectedOptionsByValueId)) ? ONE : ZERO;
            default -> throw new IllegalStateException("EXACT_MATCH не поддерживается для поля типа " + rule.getProfileField().getFieldType());
        };
    }

    private Set<Long> getSelectedOptionIds(ProfileValue value, Map<Long, Set<Long>> selectedOptionsByValueId) {
        return selectedOptionsByValueId.getOrDefault(value.getId(), Set.of());
    }

    private Set<String> tokenize(String text) {
        String normalized = normalize(text);

        if (normalized.isBlank()) {
            return Set.of();
        }

        return Arrays.stream(NON_WORD_CHARACTERS.split(normalized))
                .filter(token -> !token.isBlank())
                .collect(Collectors.toSet());
    }

    private String normalize(String text) {
        return text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
    }

    private BigDecimal jaccardSimilarity(Set<?> first, Set<?> second) {
        if (first.isEmpty() && second.isEmpty()) {
            return ZERO;
        }

        Set<Object> intersection = new HashSet<>(first);
        intersection.retainAll(second);

        Set<Object> union = new HashSet<>(first);
        union.addAll(second);

        return BigDecimal.valueOf(intersection.size())
                .divide(BigDecimal.valueOf(union.size()), 6, RoundingMode.HALF_UP);
    }

    private BigDecimal numericProximity(BigDecimal first, BigDecimal second) {
        BigDecimal maxMagnitude = first.abs().max(second.abs());

        if (maxMagnitude.compareTo(ZERO) == 0) {
            return ONE;
        }

        BigDecimal relativeDifference = first.subtract(second).abs().divide(maxMagnitude, 6, RoundingMode.HALF_UP);

        return ONE.subtract(relativeDifference).max(ZERO).min(ONE);
    }

    private void validateUsers(User mentor, User mentee) {
        if (mentor.getId().equals(mentee.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Наставник и наставляемый должны быть разными пользователями.");
        }

        if (mentor.getRole().getCode() != RoleCode.MENTOR || mentee.getRole().getCode() != RoleCode.MENTEE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Необходимо передать наставника и наставляемого с соответствующими ролями.");
        }

        if (mentor.isBlocked() || mentee.isBlocked()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Заблокированные пользователи не участвуют в подборе.");
        }

        if (mentor.getAccountStatus() != AccountStatus.ACCEPTED || mentee.getAccountStatus() != AccountStatus.ACCEPTED) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Оба пользователя должны иметь подтверждённые учётные записи.");
        }
    }

    private boolean isRuleEligible(ProfileMatchingRule rule) {
        ProfileField field = rule.getProfileField();

        return rule.isActive() && field.isActive() && field.isMatchingEnabled();
    }
}
