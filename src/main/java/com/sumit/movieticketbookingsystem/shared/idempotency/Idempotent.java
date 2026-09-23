package com.sumit.movieticketbookingsystem.shared.idempotency;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a controller method that needs an {@code Idempotency-Key} header. A retry with the same key and body gets
 * the first answer back instead of running again; see {@link IdempotencyAspect}.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Idempotent {
}
