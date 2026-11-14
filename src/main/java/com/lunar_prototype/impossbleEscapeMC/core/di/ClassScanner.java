package com.lunar_prototype.impossbleEscapeMC.core.di;

import com.lunar_prototype.impossbleEscapeMC.core.di.annotations.*;
import com.lunar_prototype.impossbleEscapeMC.core.di.annotations.Module;
import com.lunar_prototype.impossbleEscapeMC.core.di.exceptions.DependencyResolutionException;
import java.io.IOException;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.jar.JarFile;

/** 自分のパッケージの注釈付きクラスだけを、初期化せずに検出する。追加ライブラリは不要。 */
final class ClassScanner {
    private ClassScanner() {}
    static Set<Class<?>> findComponents(ClassLoader loader, String basePackage) {
        if (basePackage.isBlank()) throw new IllegalArgumentException("A base package is required");
        String prefix = basePackage.replace('.', '/');
        Set<String> names = new TreeSet<>();
        try {
            var resources = loader.getResources(prefix);
            while (resources.hasMoreElements()) collect(resources.nextElement(), basePackage, prefix, names);
            // ディレクトリエントリーが無いjarにも対応する。
            var source = DIContainer.class.getProtectionDomain().getCodeSource();
            if (source != null && source.getLocation().getProtocol().equals("file")) {
                Path path = Path.of(source.getLocation().toURI());
                if (Files.isRegularFile(path)) collectJar(path, prefix, names);
            }
            Set<Class<?>> result = new LinkedHashSet<>();
            for (String name : names) {
                Class<?> type = Class.forName(name, false, loader);
                if (type.isAnnotationPresent(Ignore.class)) continue;
                if (type.isAnnotationPresent(Component.class) || type.isAnnotationPresent(Service.class)
                        || type.isAnnotationPresent(Repository.class) || type.isAnnotationPresent(Module.class)) result.add(type);
            }
            return Collections.unmodifiableSet(result);
        } catch (IOException | URISyntaxException | ClassNotFoundException | LinkageError e) {
            throw new DependencyResolutionException("Cannot scan DI package " + basePackage, e);
        }
    }
    private static void collect(URL url, String basePackage, String prefix, Set<String> names) throws IOException, URISyntaxException {
        if (url.getProtocol().equals("file")) {
            Path root = Path.of(url.toURI());
            try (var paths = Files.walk(root)) {
                for (Path path : paths.filter(p -> p.toString().endsWith(".class")).toList()) {
                    String suffix = root.relativize(path).toString().replace(java.io.File.separatorChar, '.');
                    names.add(basePackage + "." + suffix.substring(0, suffix.length() - 6));
                }
            }
        } else if (url.getProtocol().equals("jar")) {
            JarURLConnection connection = (JarURLConnection) url.openConnection();
            connection.setUseCaches(false);
            try (JarFile jar = connection.getJarFile()) { collectEntries(jar, prefix, names); }
        }
    }
    private static void collectJar(Path path, String prefix, Set<String> names) throws IOException {
        try (JarFile jar = new JarFile(path.toFile())) { collectEntries(jar, prefix, names); }
    }
    private static void collectEntries(JarFile jar, String prefix, Set<String> names) {
        jar.stream().map(java.util.jar.JarEntry::getName)
                .filter(n -> n.startsWith(prefix + "/") && n.endsWith(".class") && !n.endsWith("package-info.class"))
                .map(n -> n.substring(0, n.length() - 6).replace('/', '.')).forEach(names::add);
    }
}
