package com.motorsure.coreapi.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;

@Documented
@Constraint(validatedBy = NotFutureDateValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface NotFutureDate {
    String message() default "must not be in the future";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
