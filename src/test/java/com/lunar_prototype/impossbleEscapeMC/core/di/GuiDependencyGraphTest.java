package com.lunar_prototype.impossbleEscapeMC.core.di;

import com.sun.source.tree.*;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TreeScanner;
import org.junit.jupiter.api.Test;
import javax.tools.ToolProvider;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;
import static org.junit.jupiter.api.Assertions.*;

/** NMSクラスをロードせず、実ソースの@Injectと旧起動順から全サービスのグラフを作る。 */
class GuiDependencyGraphTest {
    private static final String BASE = "com.lunar_prototype.impossbleEscapeMC.";
    private record Definition(String name, String pkg, Map<String, String> imports, List<String> parameters) { }
    @Test void allGuiDependenciesAreRegisteredAndStartupGraphIsAcyclic() throws Exception {
        Path root = Path.of("src/main/java");
        Map<String, Definition> definitions = new LinkedHashMap<>();
        Map<String, String> names = new HashMap<>();
        List<Path> files;
        try (var paths = Files.walk(root)) { files = paths.filter(p -> p.toString().endsWith(".java")).toList(); }
        var compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "Run with the project JDK");
        try (var manager = compiler.getStandardFileManager(null, null, null)) {
            var task = (JavacTask) compiler.getTask(null, manager, null, List.of("-proc:none"), null, manager.getJavaFileObjectsFromPaths(files));
            for (CompilationUnitTree unit : task.parse()) {
                String pkg = unit.getPackageName().toString();
                Map<String, String> imports = new HashMap<>();
                for (ImportTree imported : unit.getImports()) {
                    String name = imported.getQualifiedIdentifier().toString();
                    imports.put(name.substring(name.lastIndexOf('.') + 1), name);
                }
                new TreeScanner<Void, String>() {
                    @Override public Void visitClass(ClassTree type, String parent) {
                        String simple = type.getSimpleName().toString();
                        if (simple.isEmpty()) return super.visitClass(type, parent);
                        String name = parent == null ? pkg + "." + simple : parent + "$" + simple;
                        if (parent == null) names.put(simple, name);
                        boolean managed = type.getModifiers().getAnnotations().stream().anyMatch(a -> Set.of("Service", "Component", "Repository", "Module").contains(simple(a.getAnnotationType().toString())));
                        if (managed) {
                            List<MethodTree> constructors = type.getMembers().stream().filter(t -> t instanceof MethodTree)
                                    .map(t -> (MethodTree) t).filter(m -> m.getName().contentEquals("<init>") && m.getModifiers().getAnnotations().stream()
                                            .anyMatch(a -> simple(a.getAnnotationType().toString()).equals("Inject"))).toList();
                            assertEquals(1, constructors.size(), name);
                            definitions.put(name, new Definition(name, pkg, imports, constructors.getFirst().getParameters().stream().map(p -> p.getType().toString()).toList()));
                        }
                        return super.visitClass(type, name);
                    }
                }.scan(unit, null);
            }
        }
        assertEquals(45, definitions.size(), "All production components, including nested GUI listeners");
        String main = Files.readString(root.resolve(BASE.replace('.', '/') + "ImpossbleEscapeMC.java"));
        Matcher modules = Pattern.compile("moduleBootstrap\\.registerModule\\(([\\w.]+)\\.class\\)").matcher(main);
        List<String> ordered = new ArrayList<>();
        while (modules.find()) ordered.add(names.get(simple(modules.group(1))));
        assertEquals(23, ordered.size());
        Set<String> external = new HashSet<>(List.of("org.bukkit.plugin.java.JavaPlugin", BASE + "ImpossbleEscapeMC", "java.util.logging.Logger",
                BASE + "core.command.CommandRegistry"));
        for (String name : List.of("ScavSpawner", "MinigameManager", "LootManager", "LootBackpackOverlayListener",
                "LootEggListener", "CorpseManager", "RaidMapManager", "AiRaidLogger")) external.add(names.get(name));
        Map<String, List<String>> graph = new LinkedHashMap<>();
        for (Definition service : definitions.values()) {
            List<String> eager = new ArrayList<>();
            for (String parameter : service.parameters()) {
                Matcher supplier = Pattern.compile("(?:java\\.util\\.function\\.)?Supplier<(.+)>").matcher(parameter);
                boolean deferred = supplier.matches();
                String type = deferred ? supplier.group(1).trim() : parameter;
                String target = type.contains(".") ? type : service.imports().getOrDefault(type, service.pkg() + "." + type);
                assertTrue(definitions.containsKey(target) || external.contains(target), "Unregistered dependency: " + service.name() + " -> " + target);
                if (!deferred && definitions.containsKey(target)) eager.add(target);
            }
            int index = ordered.indexOf(service.name());
            if (index > 0) eager.add(ordered.get(index - 1));
            graph.put(service.name(), eager);
        }
        Set<String> done = new HashSet<>();
        for (String service : graph.keySet()) visit(service, graph, done, new ArrayList<>());
    }
    private static String simple(String name) { return name.substring(name.lastIndexOf('.') + 1); }
    private static void visit(String node, Map<String, List<String>> graph, Set<String> done, List<String> path) {
        if (done.contains(node)) return;
        assertFalse(path.contains(node), "Eager/start-order cycle: " + path + " -> " + node);
        path.add(node);
        for (String next : graph.get(node)) visit(next, graph, done, path);
        path.removeLast(); done.add(node);
    }
}
