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

    private final UserRepository userRepository;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final BigDecimal ONE = BigDecimal.ONE;

    private static final Pattern NON_WORD_CHARACTERS = Pattern.compile("[^\\p{L}\\p{N}]+");

    private final ProfileValueRepository profileValueRepository;
    private final ProfileValueOptionRepository profileValueOptionRepository;
    private final ProfileMatchingRuleRepository profileMatchingRuleRepository;

    public ProfileCompatibilityResponse calculate(Long mentorId, Long menteeId) {
        if (mentorId == null || menteeId == null) {
            throw new IllegalArgumentException("Необходимо указать ID наставника и наставляемого.");
        }

        if (mentorId.equals(menteeId)) {
            throw new IllegalArgumentException("Наставник и наставляемый должны быть разными пользователями.");
        }

        User mentor = userRepository.findById(mentorId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Наставник не найден."));
        User mentee = userRepository.findById(menteeId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Наставляемый не найден."));

        validateUsers(mentor, mentee);

        Map<Long, ProfileValue> mentorValues = loadValues(mentorId);
        Map<Long, ProfileValue> menteeValues = loadValues(menteeId);

        List<ProfileMatchingRule> rules = profileMatchingRuleRepository.findAllByActiveTrue();
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

            if (!hasComparableValue(mentorValue, rule) || !hasComparableValue(menteeValue, rule)) {
                skippedFieldCount++;
                continue;
            }

            BigDecimal similarity = compare(mentorValue, menteeValue, rule);
            BigDecimal weight = rule.getWeight();

            weightedScore = weightedScore.add(similarity.multiply(weight));
            totalWeight = totalWeight.add(weight);

            details.add(new ProfileCompatibilityResponse.FieldCompatibility(
                            fieldId,
                            rule.getProfileField().getName(),
                            rule.getComparisonType().name(),
                            weight,
                            similarity.multiply(HUNDRED).setScale(2, RoundingMode.HALF_UP)));
        }

        BigDecimal percentage = totalWeight.compareTo(ZERO) == 0 ? null : weightedScore
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

    private Map<Long, ProfileValue> loadValues(Long userId) {
        return profileValueRepository.findAllByUser_Id(userId)
                .stream()
                .collect(Collectors.toMap(
                        value -> value.getProfileField().getId(),
                        value -> value));
    }

    private boolean hasComparableValue(ProfileValue value, ProfileMatchingRule rule) {
        if (value == null) {
            return false;
        }

        ProfileFieldType fieldType = rule.getProfileField().getFieldType();
        return switch (fieldType) {
            case TEXT, TEXTAREA -> value.getValueText() != null && !value.getValueText().isBlank();
            case NUMBER -> value.getValueNumber() != null;
            case DATE -> value.getValueDate() != null;
            case BOOLEAN -> value.getValueBoolean() != null;
            case SELECT, MULTISELECT -> !loadSelectedOptionIds(value).isEmpty();
        };
    }

    private BigDecimal compare(ProfileValue mentor, ProfileValue mentee, ProfileMatchingRule rule) {
        return switch (rule.getComparisonType()) {
            case EXACT_MATCH -> exactMatch(mentor, mentee, rule);
            case MULTISELECT_OVERLAP -> jaccardSimilarity(loadSelectedOptionIds(mentor), loadSelectedOptionIds(mentee));
            case TEXT_WORD_OVERLAP -> jaccardSimilarity(tokenize(mentor.getValueText()), tokenize(mentee.getValueText()));
            case NUMERIC_PROXIMITY -> numericProximity(mentor.getValueNumber(), mentee.getValueNumber());
            case BOOLEAN_MATCH -> mentor.getValueBoolean().equals(mentee.getValueBoolean()) ? ONE : ZERO;
        };
    }

    private BigDecimal exactMatch(ProfileValue mentor, ProfileValue mentee, ProfileMatchingRule rule) {
        return switch (rule.getProfileField().getFieldType()) {
            case TEXT -> normalize(mentor.getValueText()).equals(normalize(mentee.getValueText())) ? ONE : ZERO;
            case SELECT -> loadSelectedOptionIds(mentor).equals(loadSelectedOptionIds(mentee)) ? ONE : ZERO;
            default -> throw new IllegalStateException("EXACT_MATCH не поддерживается для поля типа " + rule.getProfileField().getFieldType());
        };
    }

    private Set<Long> loadSelectedOptionIds(ProfileValue value) {
        return profileValueOptionRepository
                .findAllByProfileValue_Id(value.getId())
                .stream()
                .map(ProfileValueOption::getProfileOption)
                .map(option -> option.getId())
                .collect(Collectors.toSet());
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
