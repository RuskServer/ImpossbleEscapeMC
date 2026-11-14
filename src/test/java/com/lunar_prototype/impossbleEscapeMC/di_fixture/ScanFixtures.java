package com.lunar_prototype.impossbleEscapeMC.di_fixture;

import com.lunar_prototype.impossbleEscapeMC.core.di.annotations.*;

public final class ScanFixtures {
    public static int initialized;
    @Service public static class Found {
        static { initialized++; }
    }
    @Ignore @Component public static class Ignored {
        public Ignored() { throw new AssertionError("Ignored component constructed"); }
    }
    public static class Unmarked {
        public Unmarked() { throw new AssertionError("Unmarked component constructed"); }
    }
}
