package com.lunar_prototype.impossbleEscapeMC.core.di;

import com.lunar_prototype.impossbleEscapeMC.core.di.annotations.Inject;
import com.lunar_prototype.impossbleEscapeMC.core.lifecycle.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

/** レビューで見つかった再入・非同期参照・停止要求・具体型の混同を回帰検証する。 */
public class DILifecycleRegressionTest {
    static class Value {}
    static class Bridge implements Startable, Stoppable {
        Runnable starting = () -> {}, stopping = () -> {};
        int starts, stops;
        public void start() { starts++; starting.run(); }
        public void stop() { stops++; stopping.run(); }
    }
    static class After implements Startable { int starts; public void start() { starts++; } }
    static ExecutorService worker() {
        return Executors.newSingleThreadExecutor(r -> { Thread t = new Thread(r, "di-regression"); t.setDaemon(true); return t; });
    }
    static <T> T await(Future<T> future) {
        try { return future.get(3, TimeUnit.SECONDS); }
        catch (Exception e) { throw new AssertionError("Worker blocked by lifecycle callback", e); }
    }
    @Test void callbacksCanAwaitWorkerLookups() {
        var c = new DIContainer(); var worker = worker();
        Value value = c.resolve(Value.class); var bridge = new Bridge();
        AtomicInteger completed = new AtomicInteger();
        Runnable lookup = () -> {
            assertSame(value, await(worker.submit(() -> c.findInstance(Value.class))));
            assertSame(value, await(worker.submit(() -> c.resolve(Value.class))));
            completed.incrementAndGet();
        };
        bridge.starting = lookup; bridge.stopping = lookup;
        c.registerInstance(Bridge.class, bridge);
        try {
            c.startAll(); c.stopAll();
            assertEquals(2, completed.get());
            assertEquals(DIContainer.Phase.STOPPED, c.phase());
        } finally { c.stopAll(); worker.shutdownNow(); }
    }
    @Test void repeatedAndReentrantStopsRunOnce() {
        var c = new DIContainer(); var bridge = new Bridge(); var worker = worker();
        bridge.stopping = () -> {
            c.stopAll();
            await(worker.submit(c::stopAll));
        };
        c.registerInstance(Bridge.class, bridge);
        try {
            c.startAll(); c.stopAll(); c.stopAll();
            assertEquals(1, bridge.stops);
            assertEquals(DIContainer.Phase.STOPPED, c.phase());
        } finally { c.stopAll(); worker.shutdownNow(); }
    }
    @Test void stopDuringStartupCancelsAndUnwindsOnce() {
        for (boolean async : List.of(false, true)) {
            var c = new DIContainer(); var bridge = new Bridge(); var after = new After(); var worker = worker();
            bridge.starting = () -> {
                if (async) await(worker.submit(c::stopAll));
                else c.stopAll();
            };
            c.registerInstance(Bridge.class, bridge); c.registerInstance(After.class, after);
            c.startAfter(After.class, Bridge.class);
            try {
                assertThrows(CancellationException.class, c::startAll);
                assertEquals(DIContainer.Phase.STOPPED, c.phase());
                assertEquals(1, bridge.starts); assertEquals(1, bridge.stops); assertEquals(0, after.starts);
                c.stopAll(); assertEquals(1, bridge.stops);
            } finally { c.stopAll(); worker.shutdownNow(); }
        }
    }
    static class StopInConstructor {
        @Inject StopInConstructor(DIContainer c) { c.stopAll(); }
    }
    @Test void stopDuringPreparationPreventsStartup() {
        var c = new DIContainer(); var after = new After();
        c.registerTypes(StopInConstructor.class); c.registerInstance(After.class, after);
        assertThrows(CancellationException.class, c::startAll);
        assertEquals(DIContainer.Phase.STOPPED, c.phase()); assertEquals(0, after.starts);
    }
    static class Parent implements Startable, Stoppable {
        int starts, stops;
        public void start() { starts++; }
        public void stop() { stops++; }
    }
    static class Child extends Parent {}
    static class ParentClient {
        final Parent parent;
        @Inject ParentClient(Parent parent) { this.parent = parent; }
    }
    @Test void concreteComponentsAreIndependentOfRegistrationOrder() {
        for (boolean childFirst : List.of(false, true)) {
            var c = new DIContainer();
            if (childFirst) c.registerTypes(Child.class, Parent.class, ParentClient.class);
            else c.registerTypes(Parent.class, Child.class, ParentClient.class);
            c.startAll();
            Parent parent = c.resolve(Parent.class); Child child = c.resolve(Child.class);
            assertNotSame(parent, child); assertEquals(Parent.class, parent.getClass());
            assertSame(parent, c.resolve(ParentClient.class).parent);
            assertEquals(1, parent.starts); assertEquals(1, child.starts);
            c.stopAll(); assertEquals(1, parent.stops); assertEquals(1, child.stops);
        }
        var alias = new DIContainer(); var child = new Child();
        alias.registerInstance(Parent.class, child); alias.registerInstance(Child.class, child);
        alias.startAll(); alias.stopAll(); assertEquals(1, child.starts); assertEquals(1, child.stops);
    }
    interface Part {}
    static class PartImpl implements Part {}
    @Test void preparedInterfaceResolvesAtRuntime() {
        var c = new DIContainer(); c.registerTypes(PartImpl.class); c.startAll();
        assertSame(c.resolve(PartImpl.class), c.resolve(Part.class));
        c.stopAll();
    }
    public static void main(String[] args) {
        var test = new DILifecycleRegressionTest();
        test.callbacksCanAwaitWorkerLookups(); test.repeatedAndReentrantStopsRunOnce();
        test.stopDuringStartupCancelsAndUnwindsOnce(); test.stopDuringPreparationPreventsStartup();
        test.concreteComponentsAreIndependentOfRegistrationOrder(); test.preparedInterfaceResolvesAtRuntime();
        System.out.println("DILifecycleRegressionTest OK: unlocked callbacks, reentrant stop, startup cancellation, concrete identity");
    }
}
