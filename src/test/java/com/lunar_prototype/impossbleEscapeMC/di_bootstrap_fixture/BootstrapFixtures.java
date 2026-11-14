package com.lunar_prototype.impossbleEscapeMC.di_bootstrap_fixture;

import com.lunar_prototype.impossbleEscapeMC.core.*;
import com.lunar_prototype.impossbleEscapeMC.core.di.annotations.*;
import java.util.*;

public final class BootstrapFixtures {
    public interface Part {}
    @Service public static class PartImpl implements Part {}
    public static class First implements IModule {
        public static int constructed;
        public final Part part;
        public final List<Part> parts;
        public final List<String> order;
        @Inject public First(Part part, List<Part> parts, Order order) {
            constructed++; this.part = part; this.parts = parts; this.order = order.values;
        }
        public void onEnable(ServiceContainer c) { order.add("first+"); }
        public void onDisable() { order.add("first-"); }
    }
    public static class Second implements IModule {
        final List<String> order;
        @Inject public Second(Order order) { this.order = order.values; }
        public void onEnable(ServiceContainer c) { order.add("second+"); }
        public void onDisable() { order.add("second-"); }
    }
    public static class Order { public final List<String> values = new ArrayList<>(); }
}
