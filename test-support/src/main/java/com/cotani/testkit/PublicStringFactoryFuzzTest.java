package com.cotani.testkit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Fuzzes public single-string factories discovered in each module's own compiled output. */
@Tag("fuzz")
public final class PublicStringFactoryFuzzTest {
    private static final List<String> FACTORY_NAMES = List.of("of", "parse", "from", "valueOf");

    @Test
    void publicStringFactoriesAreDeterministicAndFailThroughTheirContracts() throws IOException {
        var module = Objects.requireNonNull(System.getProperty("cotani.test.module"), "cotani.test.module");
        var publicTypes = discoverPublicTypes(module);
        assertFalse(publicTypes.isEmpty(), "No public production types discovered for module " + module);
        var discoveredFactories = new ArrayList<Method>();
        var unavailablePlatformTypes = new ArrayList<String>();
        for (var type : publicTypes) {
            try {
                Arrays.stream(type.getDeclaredMethods())
                        .filter(PublicStringFactoryFuzzTest::isStringFactory)
                        .forEach(discoveredFactories::add);
            } catch (LinkageError missingOptionalPlatformType) {
                unavailablePlatformTypes.add(type.getName() + ": " + missingOptionalPlatformType.getMessage());
            }
        }
        var factories = discoveredFactories.stream()
                .sorted(Comparator.comparing(Method::toGenericString))
                .toList();

        Assumptions.assumeFalse(
                factories.isEmpty(),
                () -> "Module " + module + " exposes no single-string public factory"
                        + (unavailablePlatformTypes.isEmpty()
                                ? ""
                                : "; unavailable optional platform types=" + unavailablePlatformTypes));

        long seed = StressTestSupport.rootSeed();
        int iterations = StressTestSupport.iterations();
        for (int iteration = 0; iteration < iterations; iteration++) {
            var factory = factories.get(iteration % factories.size());
            var random = SeededRandom.scenario(seed, module, factory.toGenericString(), iteration);
            var playerId = random.uuid("factory-player");
            var context = new ScenarioContext(seed, iteration, module, factory.toGenericString(), playerId);
            var input = fuzzInput(random, iteration);
            context.verify(() -> verifyInvocation(factory, input));
        }

        factories.stream()
                .filter(factory -> Arrays.stream(factory.getParameterAnnotations()[0])
                        .noneMatch(annotation ->
                                annotation.annotationType().getSimpleName().equals("Nullable")))
                .forEach(PublicStringFactoryFuzzTest::verifyNullRejected);
    }

    private static List<Class<?>> discoverPublicTypes(String module) throws IOException {
        var outputDirectory = findModuleOutput(module);
        var types = new ArrayList<Class<?>>();
        try (var paths = Files.walk(outputDirectory)) {
            for (var path :
                    paths.filter(PublicStringFactoryFuzzTest::isLoadableClass).toList()) {
                var relative = outputDirectory.relativize(path).toString();
                var className = relative.substring(0, relative.length() - ".class".length())
                        .replace(File.separatorChar, '.');
                try {
                    var type = Class.forName(
                            className, false, Thread.currentThread().getContextClassLoader());
                    if (Modifier.isPublic(type.getModifiers())) {
                        types.add(type);
                    }
                } catch (ClassNotFoundException | LinkageError failure) {
                    throw new AssertionError(
                            "Cannot inspect public type " + className + " in module " + module, failure);
                }
            }
        }
        return List.copyOf(types);
    }

    private static Path findModuleOutput(String module) {
        var expectedSegment = ("cotani-" + module + File.separator + "build" + File.separator + "classes"
                        + File.separator + "java" + File.separator + "main")
                .toLowerCase(Locale.ROOT);
        return Arrays.stream(
                        System.getProperty("java.class.path").split(java.util.regex.Pattern.quote(File.pathSeparator)))
                .map(Path::of)
                .filter(Files::isDirectory)
                .filter(path -> path.toAbsolutePath()
                        .normalize()
                        .toString()
                        .toLowerCase(Locale.ROOT)
                        .endsWith(expectedSegment))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Cannot locate production output for module " + module));
    }

    private static boolean isLoadableClass(Path path) {
        var name = path.getFileName().toString();
        return name.endsWith(".class") && !name.equals("module-info.class") && !name.contains("$");
    }

    private static boolean isStringFactory(Method method) {
        return Modifier.isPublic(method.getModifiers())
                && Modifier.isStatic(method.getModifiers())
                && FACTORY_NAMES.contains(method.getName())
                && method.getParameterCount() == 1
                && method.getParameterTypes()[0] == String.class
                && method.getReturnType() != Void.TYPE;
    }

    private static String fuzzInput(SeededRandom random, int iteration) {
        return switch (iteration % 12) {
            case 0 -> "";
            case 1 -> " ";
            case 2 -> "\t\n";
            case 3 -> "0";
            case 4 -> "-1";
            case 5 -> "2147483647";
            case 6 -> "9223372036854775807";
            case 7 -> "<red>injection</red>";
            case 8 -> "minecraft:stone";
            case 9 -> "A".repeat(4_096);
            default -> random.input(256);
        };
    }

    private static void verifyInvocation(Method factory, String input) throws IllegalAccessException {
        try {
            var first = factory.invoke(null, input);
            var second = factory.invoke(null, input);
            assertNotNull(first, () -> factory + " returned null for input=" + printable(input));
            assertEquals(
                    first.getClass(),
                    Objects.requireNonNull(second, "second result").getClass());
            if (hasValueEquality(first.getClass())) {
                assertEquals(first, second, () -> factory + " is not deterministic for input=" + printable(input));
            }
            assertFalse(first.toString().isBlank(), () -> factory + " produced a blank representation");
        } catch (InvocationTargetException failure) {
            var cause = Objects.requireNonNull(failure.getCause(), "invocation cause");
            assertTrue(
                    cause instanceof IllegalArgumentException,
                    () -> factory + " leaked unexpected " + cause.getClass().getName() + " for input="
                            + printable(input));
        }
    }

    private static boolean hasValueEquality(Class<?> type) {
        if (type.isRecord() || type.isEnum()) {
            return true;
        }
        try {
            return type.getMethod("equals", Object.class).getDeclaringClass() != Object.class;
        } catch (NoSuchMethodException failure) {
            throw new AssertionError("Every object must expose equals(Object): " + type.getName(), failure);
        }
    }

    private static void verifyNullRejected(Method factory) {
        try {
            var result = factory.invoke(null, new Object[] {null});
            throw new AssertionError(factory + " accepted null and returned " + result);
        } catch (IllegalAccessException failure) {
            throw new AssertionError("Cannot invoke " + factory, failure);
        } catch (InvocationTargetException failure) {
            var cause = Objects.requireNonNull(failure.getCause(), "invocation cause");
            assertTrue(
                    cause instanceof NullPointerException || cause instanceof IllegalArgumentException,
                    () -> factory + " leaked unexpected null failure "
                            + cause.getClass().getName());
        }
    }

    private static String printable(String input) {
        var escaped = input.replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
        return escaped.length() <= 256 ? escaped : escaped.substring(0, 256) + "…(" + escaped.length() + ')';
    }
}
