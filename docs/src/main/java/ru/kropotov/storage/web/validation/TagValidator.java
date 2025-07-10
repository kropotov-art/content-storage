package ru.kropotov.storage.web.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.List;

import static org.springframework.util.CollectionUtils.isEmpty;

public class TagValidator implements ConstraintValidator<ValidTags, List<String>> {
    
    private static final int MAX_TAGS = 5;
    private static final String TAG_PATTERN = "^[a-zA-Z0-9_-]{1,30}$";
    
    @Override
    public boolean isValid(List<String> tags, ConstraintValidatorContext context) {
        if (isEmpty(tags)) {
            return true;
        }

        if (tags.size() > MAX_TAGS) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate("Maximum " + MAX_TAGS + " tags allowed")
                   .addConstraintViolation();
            return false;
        }

        for (String tag : tags) {
            if (tag == null || tag.trim().isEmpty()) {
                context.disableDefaultConstraintViolation();
                context.buildConstraintViolationWithTemplate("Tag cannot be null or empty")
                       .addConstraintViolation();
                return false;
            }
            
            if (!tag.matches(TAG_PATTERN)) {
                context.disableDefaultConstraintViolation();
                context.buildConstraintViolationWithTemplate(
                    "Tag '" + tag + "' must contain only alphanumeric characters, underscore, or dash and be 1-30 characters long")
                       .addConstraintViolation();
                return false;
            }
        }
        
        return true;
    }
}