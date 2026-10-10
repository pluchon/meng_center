package com.pluchon.meng.content.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class CodePointLengthValidator
    implements ConstraintValidator<CodePointLength, String>
{

    private int max;

    public void initialize(CodePointLength constraint) {
        max = constraint.max();
    }

    public boolean isValid(String value, ConstraintValidatorContext context) {
        return value == null || value.codePointCount(0, value.length()) <= max;
    }
}
