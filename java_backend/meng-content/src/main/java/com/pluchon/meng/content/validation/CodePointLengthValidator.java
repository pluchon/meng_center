package com.pluchon.meng.content.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

// 校验字符串码点长度
public class CodePointLengthValidator implements ConstraintValidator<CodePointLength, String> {

    // 码点长度上限
    private int max;

    public void initialize(CodePointLength constraint) {
        max = constraint.max();
    }

    public boolean isValid(String value, ConstraintValidatorContext context) {
        return value == null || value.codePointCount(0, value.length()) <= max;
    }
}
