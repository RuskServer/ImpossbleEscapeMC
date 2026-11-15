package com.lunar_prototype.impossbleEscapeMC.testing;

import com.sun.source.tree.*;
import com.sun.source.util.*;
import org.junit.jupiter.api.Test;
import javax.tools.ToolProvider;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** 新しい検証がmainだけになり、Maven/Actionsから漏れることを防ぐ。 */
class JUnitDiscoveryTest {
    @Test void everyTestClassHasNativeJUnitCasesAndNoMainEntryPoint() throws Exception {
        List<Path> paths;
        try (var files = Files.walk(Path.of("src/test/java"))) {
            paths = files.filter(p -> p.getFileName().toString().matches(".*Tests?\\.java")).toList();
        }
        var compiler = ToolProvider.getSystemJavaCompiler();
        try (var manager = compiler.getStandardFileManager(null, null, null)) {
            var task = (JavacTask) compiler.getTask(null, manager, null, List.of("-proc:none"), null, manager.getJavaFileObjectsFromPaths(paths));
            for (CompilationUnitTree unit : task.parse()) {
                int[] cases = {0};
                new TreeScanner<Void, Void>() {
                    @Override public Void visitMethod(MethodTree method, Void unused) {
                        assertNotEquals("main", method.getName().toString(), unit.getSourceFile().getName());
                        if (method.getModifiers().getAnnotations().stream().anyMatch(a -> Set.of("Test", "ParameterizedTest", "TestFactory", "RepeatedTest")
                                .contains(a.getAnnotationType().toString().replaceAll(".*\\.", "")))) cases[0]++;
                        return super.visitMethod(method, unused);
                    }
                    @Override public Void visitMethodInvocation(MethodInvocationTree invocation, Void unused) {
                        assertFalse(invocation.getMethodSelect().toString().endsWith(".main"), "Main wrapper: " + unit.getSourceFile().getName());
                        return super.visitMethodInvocation(invocation, unused);
                    }
                }.scan(unit, null);
                assertTrue(cases[0] > 0, "No JUnit test cases: " + unit.getSourceFile().getName());
            }
        }
    }
}
