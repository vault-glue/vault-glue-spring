package io.vaultglue.kv;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface VaultValue {

    String path();

    String key();

    String defaultValue() default "";

    boolean refresh() default false;
}
