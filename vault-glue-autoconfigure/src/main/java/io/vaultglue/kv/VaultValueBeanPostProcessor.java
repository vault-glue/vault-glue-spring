package io.vaultglue.kv;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.util.ReflectionUtils;

public class VaultValueBeanPostProcessor implements BeanPostProcessor {

    private static final Logger log = LoggerFactory.getLogger(VaultValueBeanPostProcessor.class);

    private final VaultKvOperations kvOperations;
    private final Map<String, Map<String, Object>> cache = new ConcurrentHashMap<>();

    // Tracks fields that need refresh for watch mode
    private final Map<Object, Map<Field, VaultValue>> refreshableFields = new ConcurrentHashMap<>();

    public VaultValueBeanPostProcessor(VaultKvOperations kvOperations) {
        this.kvOperations = kvOperations;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        Class<?> clazz = bean.getClass();
        ReflectionUtils.doWithFields(clazz, field -> {
            VaultValue annotation = field.getAnnotation(VaultValue.class);
            if (annotation == null) return;

            injectValue(bean, field, annotation);

            if (annotation.refresh()) {
                refreshableFields
                        .computeIfAbsent(bean, k -> new ConcurrentHashMap<>())
                        .put(field, annotation);
            }
        });
        return bean;
    }

    private void injectValue(Object bean, Field field, VaultValue annotation) {
        String path = annotation.path();
        String key = annotation.key();

        try {
            Map<String, Object> secrets = cache.computeIfAbsent(path, kvOperations::get);
            Object value = secrets.get(key);

            if (value == null && !annotation.defaultValue().isEmpty()) {
                value = annotation.defaultValue();
            }

            if (value != null) {
                ReflectionUtils.makeAccessible(field);
                ReflectionUtils.setField(field, bean, convertValue(value, field.getType()));
                log.debug("[VaultGlue] Injected @VaultValue: {}.{} from {}/{}",
                        bean.getClass().getSimpleName(), field.getName(), path, key);
            } else {
                log.warn("[VaultGlue] No value found for @VaultValue: {}/{}", path, key);
            }
        } catch (Exception e) {
            log.error("[VaultGlue] Failed to inject @VaultValue: {}/{}", path, key, e);
        }
    }

    public void refreshAll() {
        cache.clear();
        refreshableFields.forEach((bean, fields) ->
                fields.forEach((field, annotation) -> injectValue(bean, field, annotation)));
    }

    private Object convertValue(Object value, Class<?> targetType) {
        if (targetType == String.class) return value.toString();
        if (targetType == int.class || targetType == Integer.class) return Integer.valueOf(value.toString());
        if (targetType == long.class || targetType == Long.class) return Long.valueOf(value.toString());
        if (targetType == boolean.class || targetType == Boolean.class) return Boolean.valueOf(value.toString());
        if (targetType == double.class || targetType == Double.class) return Double.valueOf(value.toString());
        return value;
    }
}
