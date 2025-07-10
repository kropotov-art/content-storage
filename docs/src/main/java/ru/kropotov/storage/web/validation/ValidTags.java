package ru.kropotov.storage.web.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = TagValidator.class)
@Documented
public @interface ValidTags {
    String message() default "Invalid tags: maximum 5 tags allowed, each tag must contain only alphanumeric characters, underscore, or dash and be 1-30 characters long";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}