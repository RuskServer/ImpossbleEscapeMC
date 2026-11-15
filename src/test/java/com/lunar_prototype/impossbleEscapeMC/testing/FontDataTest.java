package com.lunar_prototype.impossbleEscapeMC.testing;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class FontDataTest {
    @TempDir Path directory;
    private String previous;
    @BeforeEach void saveProperty() { previous = System.getProperty("iem.fontWidths"); }
    @AfterEach void restoreProperty() {
        if (previous == null) System.clearProperty("iem.fontWidths");
        else System.setProperty("iem.fontWidths", previous);
    }
    @Test void missingTableIsAnErrorEvenIfFontWidthsWereLoadedEarlier() {
        System.setProperty("iem.fontWidths", directory.resolve("missing.txt").toString());
        assertThrows(IllegalStateException.class, () -> new FontData().beforeAll(null));
    }
    @Test void emptyTableCannotSilentlyUseFallbackWidths() throws Exception {
        Path empty = directory.resolve("empty.txt"); Files.writeString(empty, "# no font data\n");
        System.setProperty("iem.fontWidths", empty.toString());
        assertThrows(IllegalStateException.class, () -> new FontData().beforeAll(null));
    }
}
