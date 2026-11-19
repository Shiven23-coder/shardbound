package com.shardbound.web;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Injects the caller's {@link com.shardbound.engine.GameState}, looked up by
 * the {@code X-Session} header. With {@code required = false} the parameter is
 * null when there is no game.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface CurrentGame {

    boolean required() default true;
}
