package com.juicyslew.moonstation14.gametest.support;

import com.juicyslew.moonstation14.MoonStation14;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Collection;
import java.util.Set;
import net.minecraft.gametest.framework.GameTest;
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
    private GameTestTagRegistrar() {}

    @SubscribeEvent
    public static void register(RegisterGameTestsEvent event) {
        Set<Method> methods = new HashSet<>();
        Set<Method> afterBatchMethods = new HashSet<>();
        for (ModFileScanData scan : ModList.get().getAllScanData()) {
            for (ModFileScanData.AnnotationData annotation : scan.getAnnotations()) {
                boolean gameTest = annotation.annotationType().getDescriptor().equals(GAME_TEST_DESCRIPTOR);
                boolean afterBatch = annotation.annotationType().getDescriptor().equals("Lnet/minecraft/gametest/framework/AfterBatch;");
                if (!gameTest && !afterBatch) continue;
                try {
                    if (!annotation.clazz().getClassName().startsWith("com.juicyslew.moonstation14.")) continue;
                    Class<?> type = Class.forName(annotation.clazz().getClassName(), false, GameTestTagRegistrar.class.getClassLoader());
                    for (Method method : type.getDeclaredMethods()) {
                        if (method.isAnnotationPresent(GameTest.class)) methods.add(method);
                        if (method.isAnnotationPresent(AfterBatch.class)) afterBatchMethods.add(method);
                    }
                } catch (ClassNotFoundException e) {
                    throw new IllegalStateException("Could not load GameTest class " + annotation.clazz().getClassName(), e);
                }
            }
        }

        Set<String> requested = GameTestTagSelector.parse(System.getProperty("moonstation14.gameTestTags"));
        RegistrationSummary summary = registerMethods(methods, requested, event::register);
        selectAfterBatchMethods(methods, afterBatchMethods, requested).forEach(event::register);
        LogManager.getLogger().info("GameTests {}: requested tags={}, available tags={}, selected/discovered={}, untagged={}",
                requested.isEmpty() ? "FULL" : "FILTERED", requested.isEmpty() ? "<all>" : requested, summary.availableTags(),
                summary.selected() + "/" + methods.size(), summary.untagged());
    }

    /** Validates the complete catalogue before publishing any registration to the event. */
    public static RegistrationSummary registerMethods(Collection<Method> methods, Set<String> requested,
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
        return new RegistrationSummary(Set.copyOf(available), selection.size(), untagged);
    }

    /** Select each batch hook once when at least one test in its batch is selected. */
    public static Set<Method> selectAfterBatchMethods(Collection<Method> methods, Collection<Method> afterBatchMethods,
                                                      Set<String> requested) {
        Set<String> selectedBatches = GameTestTagSelector.select(methods, requested).stream()
                .map(method -> method.getAnnotation(GameTest.class).batch()).collect(java.util.stream.Collectors.toSet());
        return afterBatchMethods.stream()
                .filter(method -> selectedBatches.contains(method.getAnnotation(AfterBatch.class).batch()))
                .collect(java.util.stream.Collectors.toSet());
    }

    public record RegistrationSummary(Set<String> availableTags, int selected, int untagged) {}
}
