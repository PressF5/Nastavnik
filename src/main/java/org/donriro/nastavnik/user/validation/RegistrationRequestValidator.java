package org.donriro.nastavnik.user.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import lombok.RequiredArgsConstructor;
import org.donriro.nastavnik.organization.educationalorganization.repository.EducationalOrganizationRepository;
import org.donriro.nastavnik.user.dto.request.RegistrationRequest;
import org.donriro.nastavnik.user.role.RoleCode;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneOffset;

@Component
@RequiredArgsConstructor
public class RegistrationRequestValidator implements ConstraintValidator<ValidRegistration, RegistrationRequest> {

    private final EducationalOrganizationRepository educationalOrganizationRepository;
    private static final int MAX_MENTEE_AGE = 35;
    private static final String EDUCATIONAL_ORGANIZATION_ID_FIELD_NAME = "educationalOrganizationId";
    private static final String MSU_ID_FIELD_NAME = "msuId";
    private static final String BIRTHDATE_FIELD_NAME = "birthDate";
    private static final String ROLE_FIELD_NAME = "role";

    @Override
    public boolean isValid(RegistrationRequest request, ConstraintValidatorContext context) {
        if (request == null || request.role() == null) {
            return true;
        }

        context.disableDefaultConstraintViolation();

        LocalDate today = LocalDate.now(ZoneOffset.UTC);

        boolean valid = validateBirthDate(request.birthDate(), today, context);

        valid &= switch (request.role()) {
            case REGION_ADMIN -> validateRegionAdmin(context);
            case MSU_ADMIN -> validateMsuAdmin(request, context);
            case OO_ADMIN, MENTOR, MENTEE -> validateEducationalOrganizationUser(request, today, context);
        };

        return valid;
    }

    private boolean validateRegionAdmin(ConstraintValidatorContext context) {
        addViolation(context, ROLE_FIELD_NAME, "REGION_ADMIN нельзя зарегистрировать через обычную регистрацию.");
        return false;
    }

    private boolean validateMsuAdmin(RegistrationRequest request, ConstraintValidatorContext context) {
        boolean valid = true;
        if (request.msuId() == null) {
            addViolation(context, MSU_ID_FIELD_NAME, "Для MSU_ADMIN необходимо указать МСУ.");
            valid = false;
        }
        if (request.educationalOrganizationId() != null) {
            addViolation(context, EDUCATIONAL_ORGANIZATION_ID_FIELD_NAME, "Для MSU_ADMIN образовательная организация не должна быть указана.");
            valid = false;
        }
        return valid;
    }

    private boolean validateEducationalOrganizationUser(RegistrationRequest request, LocalDate today, ConstraintValidatorContext context) {
        boolean valid = true;
        valid &= validateMsuId(request, context);
        valid &= validateEducationalOrganizationId(request, context);
        valid &= validateOrganizationBelongsToMsu(request, context);
        valid &= validateMenteeAge(request, today, context);
        return valid;
    }

    private boolean validateMsuId(RegistrationRequest request, ConstraintValidatorContext context) {
        if (request.msuId() != null) {
            return true;
        }
        addViolation(context, MSU_ID_FIELD_NAME, "Необходимо указать МСУ.");
        return false;
    }

    private boolean validateEducationalOrganizationId(RegistrationRequest request, ConstraintValidatorContext context) {
        if (request.educationalOrganizationId() != null) {
            return true;
        }
        addViolation(context, EDUCATIONAL_ORGANIZATION_ID_FIELD_NAME, "Необходимо указать образовательную организацию.");
        return false;
    }

    private boolean validateOrganizationBelongsToMsu(RegistrationRequest request, ConstraintValidatorContext context) {
        if (request.msuId() == null || request.educationalOrganizationId() == null) {
            return true;
        }
        boolean belongsToMsu = educationalOrganizationRepository.existsByIdAndMsuId(request.educationalOrganizationId(), request.msuId());
        if (belongsToMsu) {
            return true;
        }
        addViolation(context, EDUCATIONAL_ORGANIZATION_ID_FIELD_NAME, "Образовательная организация не принадлежит указанному МСУ.");
        return false;
    }

    private boolean validateMenteeAge(RegistrationRequest request, LocalDate today, ConstraintValidatorContext context) {
        if (request.role() != RoleCode.MENTEE) {
            return true;
        }
        int age = Period.between(request.birthDate(), today).getYears();
        if (age <= MAX_MENTEE_AGE) {
            return true;
        }
        addViolation(context, BIRTHDATE_FIELD_NAME, "Возраст наставляемого не может превышать " + MAX_MENTEE_AGE + " лет.");
        return false;
    }

    private boolean validateBirthDate(LocalDate birthDate, LocalDate today, ConstraintValidatorContext context) {
        if (!birthDate.isAfter(today)) {
            return true;
        }
        addViolation(context, BIRTHDATE_FIELD_NAME, "Дата рождения не может быть в будущем.");
        return false;
    }

    private void addViolation(ConstraintValidatorContext context, String property, String message) {
        context
                .buildConstraintViolationWithTemplate(message)
                .addPropertyNode(property)
                .addConstraintViolation();
    }
}
