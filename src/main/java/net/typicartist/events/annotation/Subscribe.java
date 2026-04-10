package net.typicartist.events.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import net.typicartist.events.EventPriority;

@Target(value = ElementType.METHOD)
@Retention(value = RetentionPolicy.RUNTIME)
public @interface Subscribe {
    EventPriority priority() default EventPriority.NORMAL;
    boolean once() default false;
}