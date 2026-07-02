package com.cotani.config.annotation;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import java.lang.annotation.ElementType;
import java.lang.annotation.Target;
import org.junit.jupiter.api.Test;

class ConfigAnnotationTargetTest {

    @Test
    void pathAnnotationsTargetRecordComponentsOnly() {
        assertRecordComponentOnly(ConfigPath.class);
        assertRecordComponentOnly(Default.class);
        assertRecordComponentOnly(Range.class);
        assertRecordComponentOnly(Required.class);
    }

    private static void assertRecordComponentOnly(Class<?> annotationType) {
        assertArrayEquals(
                new ElementType[] {ElementType.RECORD_COMPONENT},
                annotationType.getAnnotation(Target.class).value());
    }
}
