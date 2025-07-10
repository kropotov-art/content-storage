package ru.kropotov.storage.web.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ ElementType.PARAMETER })
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = NonEmptyMultipartValidator.class)
public @interface NonEmptyFile {
    String message() default "file must not be empty";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}