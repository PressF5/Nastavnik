package org.donriro.nastavnik.user.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

@Documented
@Constraint(validatedBy = RegistrationRequestValidator.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidRegistration {
    String message() default "Некорректные данные регистрации.";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
