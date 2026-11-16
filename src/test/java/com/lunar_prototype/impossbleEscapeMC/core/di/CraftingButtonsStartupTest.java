package com.lunar_prototype.impossbleEscapeMC.core.di;

import com.sun.source.tree.*;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TreeScanner;
import org.junit.jupiter.api.Test;
import javax.tools.ToolProvider;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** NMS依存クラスをロードせず、実際の起動時依存がDIへ宣言されていることを検査する。 */
class CraftingButtonsStartupTest {
    private static final Path ROOT=Path.of("src/main/java/com/lunar_prototype/impossbleEscapeMC");
    @Test void backpackStartupDeclaresEagerButtonsDependency() throws Exception {
        var unit=parse(ROOT.resolve("modules/backpack/BackpackModule.java"));
        ClassTree type=(ClassTree)unit.getTypeDecls().getFirst();
        var constructor=type.getMembers().stream().filter(m->m instanceof MethodTree t&&t.getName().contentEquals("<init>"))
                .map(m->(MethodTree)m).findFirst().orElseThrow();
        assertTrue(constructor.getModifiers().getAnnotations().stream().anyMatch(a->a.getAnnotationType().toString().equals("Inject")));
        assertTrue(constructor.getParameters().stream().anyMatch(p->p.getType().toString().equals("CraftingGridButtons")),
                "CraftingGridButtons must be an eager constructor dependency so it starts before BackpackModule.onEnable");
    }
    @Test void listenerUsesPassedButtonsAndPluginInsteadOfStartupSingletons() throws Exception {
        var unit=parse(ROOT.resolve("modules/backpack/BackpackListener.java"));
        Set<String> calls=new HashSet<>();
        new TreeScanner<Void,Void>() {
            @Override public Void visitMethodInvocation(MethodInvocationTree node,Void unused) {
                calls.add(node.getMethodSelect().toString());return super.visitMethodInvocation(node,unused);
            }
        }.scan(unit,null);
        assertFalse(calls.contains("CraftingGridButtons.get"),"The startup listener must use the injected buttons");
        assertFalse(calls.contains("ImpossbleEscapeMC.getInstance"),"The listener must use the passed plugin");
    }
    private static CompilationUnitTree parse(Path path) throws Exception {
        var compiler=ToolProvider.getSystemJavaCompiler();assertNotNull(compiler);
        try(var manager=compiler.getStandardFileManager(null,null,null)) {
            var task=(JavacTask)compiler.getTask(null,manager,null,List.of("-proc:none"),null,manager.getJavaFileObjects(path));
            return task.parse().iterator().next();
        }
    }
}
