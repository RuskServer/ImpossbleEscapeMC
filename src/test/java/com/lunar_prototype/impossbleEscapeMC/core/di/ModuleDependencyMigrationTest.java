package com.lunar_prototype.impossbleEscapeMC.core.di;

import com.lunar_prototype.impossbleEscapeMC.core.IModule;
import com.lunar_prototype.impossbleEscapeMC.core.di.annotations.*;
import org.junit.jupiter.api.Test;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import java.util.function.Supplier;
import java.util.regex.*;
import static org.junit.jupiter.api.Assertions.*;

/** 実際の24モジュールの宣言から、隠れた依存の復活と起動順の循環を防ぐ。 */
public class ModuleDependencyMigrationTest {
    private static final Path ROOT = Path.of("src/main/java");
    @Test void allRegisteredModulesDeclareTheirDependencies() throws Exception { verify(); }
    static void verify() throws Exception {
        String bootstrap = Files.readString(ROOT.resolve("com/lunar_prototype/impossbleEscapeMC/ImpossbleEscapeMC.java"));
        Map<String, Path> files = new HashMap<>();
        try (var paths = Files.walk(ROOT)) {
            paths.filter(p -> p.toString().endsWith(".java")).forEach(p -> files.put(p.getFileName().toString().replace(".java", ""), p));
        }
        Matcher matcher = Pattern.compile("moduleBootstrap\\.registerModule\\(([\\w.]+)\\.class\\)").matcher(bootstrap);
        List<Class<?>> modules = new ArrayList<>();
        Map<Class<?>, String> sources = new HashMap<>();
        while (matcher.find()) {
            String name = matcher.group(1); name = name.substring(name.lastIndexOf('.') + 1);
            String source = Files.readString(files.get(name));
            Matcher pkg = Pattern.compile("package ([\\w.]+);").matcher(source); assertTrue(pkg.find());
            Class<?> type = Class.forName(pkg.group(1) + "." + name, false, ModuleDependencyMigrationTest.class.getClassLoader());
            modules.add(type); sources.put(type, source);
        }
        assertEquals(24, modules.size());
        Map<Class<?>, List<Class<?>>> graph = new LinkedHashMap<>();
        int deferred = 0;
        for (int index = 0; index < modules.size(); index++) {
            Class<?> module = modules.get(index);
            assertTrue(module.isAnnotationPresent(Service.class), module.getName());
            var constructors = Arrays.stream(module.getDeclaredConstructors()).filter(c -> c.isAnnotationPresent(Inject.class)).toList();
            assertEquals(1, constructors.size(), module.getName());
            assertEquals(1, module.getDeclaredConstructors().length, "No service-locator compatibility constructor: " + module);
            List<Class<?>> eager = new ArrayList<>();
            for (Type parameter : constructors.getFirst().getGenericParameterTypes()) {
                if (parameter instanceof ParameterizedType generic && generic.getRawType() == Supplier.class) {
                    Class<?> target = (Class<?>) generic.getActualTypeArguments()[0];
                    assertTrue(modules.contains(target), "Lazy target is registered: " + target);
                    deferred++;
                } else {
                    assertInstanceOf(Class.class, parameter);
                    Class<?> target = (Class<?>) parameter;
                    assertFalse(DIContainer.class.isAssignableFrom(target), "Do not inject the container: " + module);
                    if (IModule.class.isAssignableFrom(target)) {
                        assertTrue(modules.contains(target), "Eager target is registered: " + target);
                        eager.add(target);
                    }
                }
            }
            if (index > 0) eager.add(modules.get(index - 1));
            graph.put(module, eager);
            String source = sources.get(module);
            assertFalse(source.contains("getServiceContainer()"), "Hidden locator: " + module);
            assertFalse(source.contains("ImpossbleEscapeMC.getInstance()"), "Hidden singleton: " + module);
            assertFalse(Pattern.compile("(?:container|services|serviceContainer)\\.(?:get|resolve)\\(").matcher(source).find(), "Legacy lookup: " + module);
            assertFalse(source.contains("SkillModule.value("), "Skill singleton use: " + module);
            assertFalse(source.contains("PartyModule.get("), "Party singleton use: " + module);
            for (Field field : module.getDeclaredFields()) {
                boolean feature = IModule.class.isAssignableFrom(field.getType()) || field.getType() == Supplier.class;
                if (feature && !Modifier.isStatic(field.getModifiers())) assertTrue(Modifier.isFinal(field.getModifiers()), "Dependency must be final: " + field);
            }
        }
        assertTrue(deferred >= 10, "Runtime-only dependencies remain explicit");
        Set<Class<?>> done = new HashSet<>();
        for (Class<?> module : modules) visit(module, graph, done, new ArrayList<>());
        for (String helper : List.of("RaidInstance", "RaidHud", "RaidRadio", "BossEncounter", "BackpackDisplayManager", "StatusEffectManager")) {
            String source = Files.readString(files.get(helper));
            assertFalse(source.contains("getServiceContainer()"), "Owned helper locator: " + helper);
            assertFalse(source.contains("ImpossbleEscapeMC.getInstance()"), "Owned helper singleton: " + helper);
        }
    }
    private static void visit(Class<?> node, Map<Class<?>, List<Class<?>>> graph, Set<Class<?>> done, List<Class<?>> path) {
        if (done.contains(node)) return;
        assertFalse(path.contains(node), "Eager/order cycle: " + path + " -> " + node);
        path.add(node);
        for (Class<?> next : graph.getOrDefault(node, List.of())) visit(next, graph, done, path);
        path.removeLast(); done.add(node);
    }

}
