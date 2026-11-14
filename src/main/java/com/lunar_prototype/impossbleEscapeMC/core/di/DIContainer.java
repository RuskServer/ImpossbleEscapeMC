package com.lunar_prototype.impossbleEscapeMC.core.di;

import com.lunar_prototype.impossbleEscapeMC.core.di.annotations.Inject;
import com.lunar_prototype.impossbleEscapeMC.core.di.exceptions.*;
import com.lunar_prototype.impossbleEscapeMC.core.lifecycle.Startable;
import com.lunar_prototype.impossbleEscapeMC.core.lifecycle.Stoppable;

import java.lang.reflect.*;
import java.util.*;
import java.util.function.Supplier;
import java.util.concurrent.CancellationException;
import java.util.logging.Logger;

/**
 * Deepwither_V2方式のコンストラクタDI。インスタンスはコンテナ内で共有する。
 * Supplierの辺は生成・起動順から分離し、全体の起動後にだけ取得を許可する。
 */
public class DIContainer implements AutoCloseable {
    public enum Phase { CONFIGURING, PREPARING, STARTING, RUNNING, STOPPING, STOPPED, FAILED }
    public record Dependency(Class<?> type, boolean deferred) {}
    private final Map<Class<?>, Object> instances = new LinkedHashMap<>();
    private final Set<Class<?>> definitions = new LinkedHashSet<>();
    private final Map<Class<?>, List<Dependency>> graph = new LinkedHashMap<>();
    private final Map<Class<?>, Set<Class<?>>> ordering = new LinkedHashMap<>();
    private final List<Class<?>> resolving = new ArrayList<>();
    private final List<Object> started = new ArrayList<>();
    private volatile Phase phase = Phase.CONFIGURING;
    private Logger logger;
    private Thread startupThread;
    private boolean stopRequested;

    public DIContainer() { registerInstance(DIContainer.class, this); }
    public synchronized Phase phase() { return phase; }
    public synchronized void setLogger(Logger logger) { this.logger = logger; }

    public synchronized <T> void registerInstance(Class<T> type, T instance) {
        Objects.requireNonNull(type); Objects.requireNonNull(instance);
        if (!type.isInstance(instance)) throw failure("Invalid instance for " + type.getName());
        Object previous = instances.get(type);
        if (previous == instance) return;
        configuring();
        if (previous != null) throw failure("Already registered: " + type.getName());
        instances.put(type, instance);
        graph.putIfAbsent(type, List.of());
    }
    public synchronized void registerTypes(Class<?>... types) {
        configuring();
        for (Class<?> type : types) {
            if (type.isInterface() || Modifier.isAbstract(type.getModifiers()))
                throw failure("Component must be concrete: " + type.getName());
            definitions.add(type);
        }
    }
    public synchronized Set<Class<?>> scan(ClassLoader loader, String basePackage) {
        configuring();
        Set<Class<?>> found = ClassScanner.findComponents(loader, basePackage);
        registerTypes(found.toArray(Class<?>[]::new));
        return found;
    }
    /** 既存モジュールの明示的な起動順も、依存グラフで検証する。 */
    public synchronized void startAfter(Class<?> type, Class<?> dependency) {
        configuring();
        if (type == dependency) throw failure("A component cannot start after itself: " + type.getName());
        ordering.computeIfAbsent(type, ignored -> new LinkedHashSet<>()).add(dependency);
    }
    /** 既存getとの互換用。未登録ならnull、複数の実装がある場合は曖昧さを拒否する。 */
    public synchronized <T> T findInstance(Class<T> type) {
        Object exact = instances.get(type);
        if (exact != null) return type.cast(exact);
        List<T> found = new ArrayList<>();
        Set<Object> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Object instance : instances.values())
            if (type.isInstance(instance) && seen.add(instance)) found.add(type.cast(instance));
        if (found.size() > 1) throw failure("Ambiguous registered service: " + type.getName());
        return found.isEmpty() ? null : found.getFirst();
    }
    public synchronized <T> T resolve(Class<T> type) {
        if (phase == Phase.STOPPED || phase == Phase.FAILED) throw failure("Container is " + phase);
        Object exact = instances.get(type);
        if (exact != null) return type.cast(exact);
        // 具体クラスの要求を子クラスで代用しない。別名登録だけは同じ実装を再利用する。
        if (concrete(type)) {
            T existing = concreteInstance(type);
            if (existing != null) return existing;
        } else {
            return type.cast(resolve(implementation(type)));
        }
        if (phase != Phase.CONFIGURING && !(phase == Phase.PREPARING && startupThread == Thread.currentThread()))
            throw failure("Component was not prepared: " + type.getName());
        Class<?> actual = implementation(type);
        int cycle = resolving.indexOf(actual);
        if (cycle >= 0) throw cycle(resolving.subList(cycle, resolving.size()), actual);
        resolving.add(actual);
        try {
            Constructor<?> constructor = constructor(actual);
            List<Dependency> dependencies = new ArrayList<>();
            Type[] parameters = constructor.getGenericParameterTypes();
            Object[] arguments = new Object[parameters.length];
            for (int i = 0; i < parameters.length; i++) {
                Type parameter = parameters[i];
                if (parameter instanceof ParameterizedType generic && generic.getRawType() == Supplier.class) {
                    Class<?> target = elementType(generic);
                    dependencies.add(new Dependency(target, true));
                    arguments[i] = (Supplier<?>) () -> deferred(target);
                } else if (parameter instanceof ParameterizedType generic && generic.getRawType() == List.class) {
                    Class<?> element = elementType(generic);
                    List<Class<?>> candidates = candidates(element);
                    List<Object> values = new ArrayList<>();
                    Set<Object> seen = Collections.newSetFromMap(new IdentityHashMap<>());
                    for (Class<?> candidate : candidates) {
                        Object value = resolve(candidate);
                        dependencies.add(new Dependency(candidate, false));
                        if (seen.add(value)) values.add(value);
                    }
                    arguments[i] = List.copyOf(values);
                } else if (parameter instanceof Class<?> target && target != Supplier.class && target != List.class) {
                    dependencies.add(new Dependency(target, false));
                    arguments[i] = resolve(target);
                } else throw failure("Unsupported injection parameter " + parameter + " in " + actual.getName());
            }
            constructor.setAccessible(true);
            Object instance = constructor.newInstance(arguments);
            if (phase != Phase.CONFIGURING && phase != Phase.PREPARING)
                throw failure("Container changed during construction: " + phase);
            instances.put(actual, instance);
            graph.put(actual, List.copyOf(dependencies));
            return type.cast(instance);
        } catch (InvocationTargetException e) {
            throw new DependencyResolutionException("Constructor failed: " + actual.getName(), e.getCause());
        } catch (ReflectiveOperationException e) {
            throw new DependencyResolutionException("Cannot instantiate: " + actual.getName(), e);
        } finally {
            resolving.removeLast();
        }
    }
    public synchronized <T> List<T> resolveAll(Class<T> type) {
        Set<Object> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        List<T> result = new ArrayList<>();
        for (Class<?> candidate : candidates(type)) {
            T instance = type.cast(resolve(candidate));
            if (seen.add(instance)) result.add(instance);
        }
        return List.copyOf(result);
    }
    private Object deferred(Class<?> target) {
        // 起動スレッドがロックを保持していても、別スレッドの早期取得を待たせない。
        if (phase != Phase.RUNNING)
            throw failure("Deferred dependency " + target.getName() + " is available only after startup (" + phase + ")");
        synchronized (this) {
            if (phase != Phase.RUNNING) throw failure("Container is " + phase);
            return resolve(target);
        }
    }
    private static Class<?> elementType(ParameterizedType type) {
        Type element = type.getActualTypeArguments()[0];
        if (element instanceof Class<?> clazz) return clazz;
        throw failure("Injection requires a concrete generic type: " + type);
    }
    private Class<?> implementation(Class<?> type) {
        if (concrete(type)) {
            if (type.isPrimitive() || type.isArray() || type.getName().startsWith("java."))
                throw failure("Register a value explicitly for " + type.getName());
            return type;
        }
        List<Class<?>> candidates = candidates(type);
        if (candidates.size() != 1) throw failure((candidates.isEmpty() ? "No implementation: " : "Ambiguous implementations: ") + type.getName() + " " + candidates);
        return candidates.getFirst();
    }
    private static boolean concrete(Class<?> type) {
        return !type.isInterface() && !Modifier.isAbstract(type.getModifiers());
    }
    private <T> T concreteInstance(Class<T> type) {
        T found = null;
        for (Object instance : instances.values()) if (instance.getClass() == type) {
            if (found != null && found != instance) throw failure("Ambiguous registered implementation: " + type.getName());
            found = type.cast(instance);
        }
        return found;
    }
    private List<Class<?>> candidates(Class<?> type) {
        Set<Class<?>> found = new LinkedHashSet<>();
        for (Class<?> candidate : definitions) if (type.isAssignableFrom(candidate)) found.add(candidate);
        // インターフェース等の別名を実装クラスへ揃え、同じ候補を重複させない。
        for (Object instance : instances.values()) if (type.isInstance(instance)) found.add(instance.getClass());
        return found.stream().sorted(Comparator.comparing(Class::getName)).toList();
    }
    private static Constructor<?> constructor(Class<?> type) {
        var injected = Arrays.stream(type.getDeclaredConstructors()).filter(c -> c.isAnnotationPresent(Inject.class)).toList();
        if (injected.size() > 1) throw failure("Multiple @Inject constructors: " + type.getName());
        if (injected.size() == 1) return injected.getFirst();
        try { return type.getDeclaredConstructor(); }
        catch (NoSuchMethodException e) { throw failure("No @Inject or no-arg constructor: " + type.getName()); }
    }
    /** 遅延依存も先に生成・検証し、実行中に新しいサービスを生成しない。 */
    private void prepare() {
        for (Class<?> type : List.copyOf(definitions)) resolve(type);
        int size;
        do {
            size = instances.size();
            for (var dependencies : List.copyOf(graph.values()))
                for (Dependency dependency : dependencies) if (dependency.deferred()) resolve(dependency.type());
        } while (size != instances.size());
    }
    public void startAll() {
        synchronized (this) {
            configuring();
            phase = Phase.PREPARING;
            startupThread = Thread.currentThread();
        }
        try {
            prepare();
            List<Object> order;
            synchronized (this) {
                checkStartupCancellation();
                order = startOrder();
                phase = Phase.STARTING;
            }
            for (Object instance : order) {
                synchronized (this) {
                    checkStartupCancellation();
                    // 部分的に起動して失敗した場合も、同じ停止経路で一度だけ解放する。
                    started.add(instance);
                }
                startInstance(instance);
            }
            synchronized (this) {
                checkStartupCancellation();
                phase = Phase.RUNNING;
                startupThread = null;
            }
        } catch (RuntimeException | Error failure) {
            synchronized (this) { phase = Phase.STOPPING; }
            stopStarted(failure);
            synchronized (this) {
                phase = failure instanceof CancellationException && stopRequested ? Phase.STOPPED : Phase.FAILED;
                startupThread = null;
            }
            throw failure;
        }
    }
    private void checkStartupCancellation() {
        if (stopRequested) throw new CancellationException("Container startup was stopped");
    }
    protected void startInstance(Object instance) {
        if (instance instanceof Startable startable) startable.start();
    }
    protected void stopInstance(Object instance) {
        if (instance instanceof Stoppable stoppable) stoppable.stop();
    }
    public void stopAll() {
        synchronized (this) {
            if (phase == Phase.STOPPING || phase == Phase.STOPPED || phase == Phase.FAILED) return;
            if (phase == Phase.PREPARING || phase == Phase.STARTING) {
                // 実行中の起動処理を並列に壊さず、起動スレッドへ停止を引き渡す。
                stopRequested = true;
                return;
            }
            phase = Phase.STOPPING;
        }
        stopStarted(null);
        synchronized (this) { phase = Phase.STOPPED; }
    }
    private void stopStarted(Throwable failure) {
        while (true) {
            Object instance;
            synchronized (this) {
                if (started.isEmpty()) return;
                instance = started.removeLast();
            }
            // ユーザーのライフサイクル処理をコンテナのモニター内で呼ばない。
            try { stopInstance(instance); }
            catch (RuntimeException | Error e) {
                if (failure != null && failure != e) failure.addSuppressed(e);
                Logger current;
                synchronized (this) { current = logger; }
                if (current != null) current.log(java.util.logging.Level.SEVERE, "Failed to stop " + instance.getClass().getName(), e);
            }
        }
    }
    private List<Object> startOrder() {
        List<Object> order = new ArrayList<>();
        Set<Class<?>> done = new HashSet<>();
        Set<Object> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Class<?> type : instances.keySet()) visit(type, done, new ArrayList<>(), order, seen);
        return order;
    }
    private void visit(Class<?> type, Set<Class<?>> done, List<Class<?>> path, List<Object> order, Set<Object> seen) {
        // 同じインスタンスの別名登録でも、その実装の依存を先に起動する。
        Object value = instances.get(type);
        if (value != null && instances.get(value.getClass()) == value) type = value.getClass();
        if (done.contains(type)) return;
        if (path.contains(type)) throw cycle(path, type);
        path.add(type);
        for (Dependency dependency : graph.getOrDefault(type, List.of())) if (!dependency.deferred()) {
            Object target = findInstance(dependency.type());
            Class<?> key = instances.containsKey(dependency.type()) ? dependency.type() : null;
            if (key == null && target != null) {
                for (var entry : instances.entrySet()) if (entry.getValue() == target) {
                    key = entry.getKey();
                    break;
                }
            }
            if (key == null || !instances.containsKey(key)) throw failure("Missing startup dependency: " + dependency.type().getName());
            visit(key, done, path, order, seen);
        }
        for (Class<?> dependency : ordering.getOrDefault(type, Set.of())) {
            if (!instances.containsKey(dependency)) throw failure("Missing ordered component: " + dependency.getName());
            visit(dependency, done, path, order, seen);
        }
        path.removeLast(); done.add(type);
        Object instance = instances.get(type);
        if (seen.add(instance)) order.add(instance);
    }
    public synchronized Collection<Object> getAllInstances() {
        Set<Object> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        return instances.values().stream().filter(seen::add).toList();
    }
    public synchronized Map<Class<?>, List<Dependency>> dependencyGraph() { return Map.copyOf(graph); }
    public synchronized void printDependencyTree() {
        if (logger == null) return;
        for (var entry : graph.entrySet()) logger.info("[DI] " + entry.getKey().getSimpleName() + " -> " + entry.getValue().stream()
                .map(d -> (d.deferred() ? "Supplier<" : "") + d.type().getSimpleName() + (d.deferred() ? ">" : "")).toList());
        for (var entry : ordering.entrySet()) logger.info("[DI order] " + entry.getKey().getSimpleName()
                + " after " + entry.getValue().stream().map(Class::getSimpleName).toList());
    }
    private void configuring() { if (phase != Phase.CONFIGURING) throw failure("Container is " + phase); }
    private static DependencyResolutionException failure(String message) { return new DependencyResolutionException(message); }
    private static CircularDependencyException cycle(List<Class<?>> path, Class<?> repeated) {
        List<String> names = new ArrayList<>(path.stream().map(Class::getSimpleName).toList()); names.add(repeated.getSimpleName());
        return new CircularDependencyException("Circular dependency: " + String.join(" -> ", names));
    }
    @Override public void close() { stopAll(); }
}
