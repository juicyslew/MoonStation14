package com.juicyslew.moonstation14.gametest.support;

import com.juicyslew.moonstation14.MoonStation14;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Collection;
import java.util.Set;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.BeforeBatch;
import net.minecraft.gametest.framework.AfterBatch;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforgespi.language.ModFileScanData;
import org.apache.logging.log4j.LogManager;

/** Explicit replacement for GameTestHolder auto-discovery; always discovers untagged tests too. */
@EventBusSubscriber(modid = MoonStation14.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class GameTestTagRegistrar {
    private static final String GAME_TEST_DESCRIPTOR = "Lnet/minecraft/gametest/framework/GameTest;";
    private static final String BEFORE_BATCH_DESCRIPTOR = "Lnet/minecraft/gametest/framework/BeforeBatch;";
    private static final String AFTER_BATCH_DESCRIPTOR = "Lnet/minecraft/gametest/framework/AfterBatch;";
    private GameTestTagRegistrar() {}

    @SubscribeEvent
    public static void register(RegisterGameTestsEvent event) {
        Set<Method> methods = new HashSet<>();
        Set<Method> callbacks = new HashSet<>();
        for (ModFileScanData scan : ModList.get().getAllScanData()) {
            for (ModFileScanData.AnnotationData annotation : scan.getAnnotations()) {
                String descriptor = annotation.annotationType().getDescriptor();
                if (!descriptor.equals(GAME_TEST_DESCRIPTOR) && !descriptor.equals(BEFORE_BATCH_DESCRIPTOR) && !descriptor.equals(AFTER_BATCH_DESCRIPTOR)) continue;
                try {
                    if (!annotation.clazz().getClassName().startsWith("com.juicyslew.moonstation14.")) continue;
                    Class<?> type = Class.forName(annotation.clazz().getClassName(), false, GameTestTagRegistrar.class.getClassLoader());
                    for (Method method : type.getDeclaredMethods()) {
                        if (method.isAnnotationPresent(GameTest.class)) methods.add(method);
                        if (method.isAnnotationPresent(BeforeBatch.class) || method.isAnnotationPresent(AfterBatch.class)) callbacks.add(method);
                    }
                } catch (ClassNotFoundException e) {
                    throw new IllegalStateException("Could not load GameTest class " + annotation.clazz().getClassName(), e);
                }
            }
        }

        Set<String> requested = GameTestTagSelector.parse(System.getProperty("moonstation14.gameTestTags"));
        RegistrationSummary summary = registerMethods(methods, requested, callbacks, event::register);
        LogManager.getLogger().info(selectionPreflight(summary, methods.size()));
        LogManager.getLogger().info("GameTests {}: requested tags={}, available tags={}, selected/discovered={}, untagged={}",
                requested.isEmpty() ? "FULL" : "FILTERED", requested.isEmpty() ? "<all>" : requested, summary.availableTags(),
                summary.selected() + "/" + methods.size(), summary.untagged());
    }

    /** Validates the complete catalogue before publishing any registration to the event. */
    public static RegistrationSummary registerMethods(Collection<Method> methods, Set<String> requested,
                                                       java.util.function.Consumer<Method> publisher) {
        return registerMethods(methods, requested, Set.of(), publisher);
    }

    /** Registers batch callbacks only when their batch has at least one selected ordinary test. */
    public static RegistrationSummary registerMethods(Collection<Method> methods, Set<String> requested,
                                                       Collection<Method> callbacks,
                                                       java.util.function.Consumer<Method> publisher) {
        Set<String> available = new HashSet<>();
        int untagged = 0;
        for (Method method : methods) {
            Set<String> methodTags = GameTestTagSelector.tags(method.getDeclaringClass(), method);
            available.addAll(methodTags);
            if (methodTags.isEmpty()) untagged++;
        }
        Set<String> unknown = GameTestTagSelector.unknownTags(requested, available);
        if (!unknown.isEmpty()) throw new IllegalStateException("Unknown GameTest tags " + unknown + "; available tags: " + available);
        Set<Method> selection = GameTestTagSelector.select(methods, requested);
        if (!requested.isEmpty() && selection.isEmpty()) throw new IllegalStateException("GameTest tag filter selected zero tests: " + requested);
        selection.forEach(publisher);
        if (!callbacks.isEmpty()) {
            Set<String> batches = new HashSet<>();
            for (Method method : selection) {
                GameTest gameTest = method.getAnnotation(GameTest.class);
                if (gameTest != null) batches.add(gameTest.batch());
            }
            for (Method callback : callbacks) {
                String batch = callback.isAnnotationPresent(BeforeBatch.class)
                        ? callback.getAnnotation(BeforeBatch.class).batch() : callback.getAnnotation(AfterBatch.class).batch();
                if (batches.contains(batch)) publisher.accept(callback);
            }
        }
        return new RegistrationSummary(Set.copyOf(available), selection.size(), untagged);
    }

    public static Set<Method> selectAfterBatchMethods(Collection<Method> methods, Collection<Method> hooks,
                                                       Set<String> requested) {
        Set<String> batches = GameTestTagSelector.select(methods, requested).stream()
                .map(method -> method.getAnnotation(GameTest.class).batch())
                .collect(java.util.stream.Collectors.toSet());
        return hooks.stream().filter(method -> method.isAnnotationPresent(AfterBatch.class)
                && batches.contains(method.getAnnotation(AfterBatch.class).batch()))
                .collect(java.util.stream.Collectors.toSet());
    }

    public record RegistrationSummary(Set<String> availableTags, int selected, int untagged) {}

    public static String selectionPreflight(RegistrationSummary summary, int discovered) {
        return "GameTest tag preflight: %d/%d fixtures selected".formatted(summary.selected(), discovered);
    }
}
