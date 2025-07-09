package ru.kropotov.storage.web.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.springframework.web.multipart.MultipartFile;

public class NonEmptyMultipartValidator
        implements ConstraintValidator<NonEmptyFile, MultipartFile> {

    @Override public boolean isValid(MultipartFile file, ConstraintValidatorContext c) {
        return file != null && !file.isEmpty();
    }
}