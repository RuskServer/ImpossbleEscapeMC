package com.lunar_prototype.impossbleEscapeMC.core.di;

import com.lunar_prototype.impossbleEscapeMC.core.*;
import com.lunar_prototype.impossbleEscapeMC.core.di.annotations.Inject;
import com.lunar_prototype.impossbleEscapeMC.core.di.exceptions.*;
import com.lunar_prototype.impossbleEscapeMC.core.lifecycle.*;
import com.lunar_prototype.impossbleEscapeMC.di_fixture.ScanFixtures;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.function.Supplier;

/** 循環、遅延取得、ライフサイクル失敗、旧コンテナとの同一性を実際に検証する。 */
public final class DIContainerTest {
    static final List<String> calls = new ArrayList<>();
    static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    static <T extends Throwable> T expect(Class<T> type, Runnable work) {
        try { work.run(); } catch (Throwable e) {
            if (type.isInstance(e)) return type.cast(e);
            throw new AssertionError("Expected " + type.getName(), e);
        }
        throw new AssertionError("Expected " + type.getName());
    }
    static class Base implements Startable, Stoppable {
        public void start() { calls.add("base+"); }
        public void stop() { calls.add("base-"); }
    }
    static class Client implements Startable, Stoppable {
        final Base base;
        @Inject Client(Base base) { this.base = base; }
        public void start() { calls.add("client+"); }
        public void stop() { calls.add("client-"); }
    }
    static class CycleA { @Inject CycleA(CycleB b) {} }
    static class CycleB { @Inject CycleB(CycleA a) {} }
    static class Raid {
        final Tasks tasks;
        @Inject Raid(Tasks tasks) { this.tasks = tasks; }
    }
    static class Tasks {
        final Supplier<Raid> raids;
        @Inject Tasks(Supplier<Raid> raids) { this.raids = raids; }
    }
    static class Premature {
        @Inject Premature(Supplier<Base> base) { base.get(); }
    }
    static class PrematureStart implements Startable {
        final Supplier<Base> base;
        @Inject PrematureStart(Supplier<Base> base) { this.base = base; }
        public void start() { base.get(); }
    }
    interface Contract {}
    static class One implements Contract {}
    static class Two implements Contract {}
    static class CollectionClient {
        final List<Contract> values;
        @Inject CollectionClient(List<Contract> values) { this.values = values; }
    }
    static class ContractClient { @Inject ContractClient(Contract contract) {} }
    static class ConcreteClient { @Inject ConcreteClient(One one) {} }
    static class Missing { @Inject Missing(Supplier<Contract> contract) {} }
    static class Broken implements Startable, Stoppable {
        @Inject Broken(Base base) {}
        public void start() { calls.add("broken+"); throw new IllegalStateException("test"); }
        public void stop() { calls.add("broken-"); }
    }
    static class StopsBadly implements Stoppable {
        public void stop() { calls.add("bad-"); throw new IllegalStateException("stop"); }
    }
    static class Multiple {
        @Inject Multiple() {}
        @Inject Multiple(Base base) {}
    }
    static class Raw { @Inject Raw(Supplier supplier) {} }
    static class First implements IModule {
        public void onEnable(ServiceContainer container) { calls.add("first+"); container.register(First.class, this); }
        public void onDisable() { calls.add("first-"); }
    }
    static class Second implements IModule {
        public void onEnable(ServiceContainer container) { check(container.get(First.class) != null, "legacy get"); calls.add("second+"); }
        public void onDisable() { calls.add("second-"); }
    }
    @Test void suite() { main(new String[0]); }
    public static void main(String[] args) {
        DIContainer c = new DIContainer();
        Client client = c.resolve(Client.class);
        check(client == c.resolve(Client.class) && client.base == c.resolve(Base.class), "singleton identity");
        calls.clear(); c.startAll(); c.stopAll(); c.stopAll();
        check(calls.equals(List.of("base+", "client+", "client-", "base-")), "dependency start and reverse stop, once: " + calls);
        expect(DependencyResolutionException.class, () -> c.resolve(Client.class));

        DIContainer cyclic = new DIContainer();
        var cycle = expect(CircularDependencyException.class, () -> cyclic.resolve(CycleA.class));
        check(cycle.getMessage().contains("CycleA -> CycleB -> CycleA"), "cycle path");
        check(cyclic.resolve(Base.class) != null, "resolution stack restored after failure");

        DIContainer lazy = new DIContainer();
        Raid raid = lazy.resolve(Raid.class);
        expect(DependencyResolutionException.class, raid.tasks.raids::get);
        lazy.startAll();
        check(raid.tasks.raids.get() == raid, "deferred back-reference is same instance");
        check(lazy.dependencyGraph().get(Tasks.class).getFirst().deferred(), "deferred edge visible");
        lazy.stopAll(); expect(DependencyResolutionException.class, raid.tasks.raids::get);
        expect(DependencyResolutionException.class, () -> new DIContainer().resolve(Premature.class));
        DIContainer earlyStart = new DIContainer(); earlyStart.registerTypes(PrematureStart.class);
        expect(DependencyResolutionException.class, earlyStart::startAll);
        check(earlyStart.phase() == DIContainer.Phase.FAILED, "early provider access fails startup");

        DIContainer list = new DIContainer(); list.registerTypes(Two.class, One.class);
        check(list.resolve(CollectionClient.class).values.getFirst() instanceof One, "stable collection injection order");
        expect(DependencyResolutionException.class, () -> list.resolve(ContractClient.class));
        DIContainer absent = new DIContainer(); absent.registerTypes(Missing.class);
        expect(DependencyResolutionException.class, absent::startAll);
        check(absent.phase() == DIContainer.Phase.FAILED, "missing lazy target rejected before start");
        DIContainer explicit = new DIContainer(); One one = new One(); explicit.registerInstance(Contract.class, one);
        check(explicit.resolve(Contract.class) == one && explicit.resolve(One.class) == one, "explicit binding shared");
        expect(DependencyResolutionException.class, () -> explicit.registerInstance(Contract.class, new Two()));
        explicit.resolve(ConcreteClient.class); explicit.startAll(); explicit.stopAll();
        expect(DependencyResolutionException.class, () -> new DIContainer().resolve(Multiple.class));
        expect(DependencyResolutionException.class, () -> new DIContainer().resolve(Raw.class));

        DIContainer failure = new DIContainer(); failure.registerTypes(Broken.class); calls.clear();
        expect(IllegalStateException.class, failure::startAll);
        check(calls.equals(List.of("base+", "broken+", "broken-", "base-")), "rollback only started/partial components: " + calls);
        failure.stopAll(); check(calls.size() == 4, "rollback not repeated");
        DIContainer stopFailure = new DIContainer(); stopFailure.resolve(StopsBadly.class); stopFailure.resolve(Base.class);
        stopFailure.startAll(); calls.clear(); stopFailure.stopAll(); check(calls.containsAll(List.of("bad-", "base-")), "stop failures isolated");

        ServiceContainer legacy = new ServiceContainer();
        check(legacy.get(Raid.class) == null, "legacy nullable lookup does not instantiate");
        check(legacy.resolve(ServiceContainer.class) == legacy && legacy.resolve(DIContainer.class) == legacy, "single shared container");
        legacy.registerTypes(Second.class, First.class); legacy.startAfter(Second.class, First.class); calls.clear();
        legacy.startAll(); legacy.stopAll();
        check(calls.equals(List.of("first+", "second+", "second-", "first-")), "legacy lifecycle ordering and self registration");
        DIContainer orderedCycle = new DIContainer(); orderedCycle.registerTypes(Base.class, Client.class); orderedCycle.startAfter(Base.class, Client.class);
        expect(CircularDependencyException.class, orderedCycle::startAll);

        DIContainer scanning = new DIContainer();
        var found = scanning.scan(DIContainerTest.class.getClassLoader(), "com.lunar_prototype.impossbleEscapeMC.di_fixture");
        check(found.equals(Set.of(ScanFixtures.Found.class)), "only marked, not ignored components");
        check(ScanFixtures.initialized == 0, "scan does not initialize classes");
        scanning.startAll(); check(ScanFixtures.initialized == 1, "resolve initializes scanned singleton"); scanning.stopAll();
        System.out.println("DIContainerTest OK: singleton, cycles, deferred references, collections, rollback, legacy bridge, scanning");
    }
}
