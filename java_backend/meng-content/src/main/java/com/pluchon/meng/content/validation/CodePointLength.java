package com.pluchon.meng.content.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;

@Target({
    ElementType.FIELD,
    ElementType.PARAMETER,
    ElementType.RECORD_COMPONENT,
})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = CodePointLengthValidator.class)
public @interface CodePointLength {
    int max();

    String message() default "invalid text length";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
