package com.pragma.utils;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

import java.util.List;
import java.util.Set;

public class ValidationHelper {

    private static final Validator validator;

    static {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    private ValidationHelper() {}

    public static <T> List<String> validate(T object) {
        Set<ConstraintViolation<T>> violations = validator.validate(object);

        return violations.stream()
                .map(ConstraintViolation::getMessage)
                .toList();
    }
}
