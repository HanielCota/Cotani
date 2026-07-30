package com.cotani.api;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a public type that exists only to connect implementation packages.
 *
 * <p>Types carrying this annotation are not part of Cotani's supported compatibility surface and
 * must not be imported by consumers.
 */
@Documented
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.TYPE)
public @interface InternalApi {}
