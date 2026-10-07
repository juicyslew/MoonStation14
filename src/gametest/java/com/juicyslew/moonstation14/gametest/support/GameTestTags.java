package com.juicyslew.moonstation14.gametest.support;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Categorizes a GameTest for optional tag-based selection. Class and method tags are combined. */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface GameTestTags {
    String[] value();
}
