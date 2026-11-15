package com.lunar_prototype.impossbleEscapeMC.testing;

import com.lunar_prototype.impossbleEscapeMC.util.FontWidths;
import org.junit.jupiter.api.extension.*;
import java.nio.file.*;

/** 実フォントの配置検証では、代替幅で成功扱いにしない。 */
public final class FontData implements BeforeAllCallback {
    @Override public void beforeAll(ExtensionContext context) throws Exception {
        String path = System.getProperty("iem.fontWidths", System.getenv("IEM_FONT_WIDTHS"));
        if (path == null || path.isBlank() || !Files.isRegularFile(Path.of(path)))
            throw new IllegalStateException("font-tests requires IEM_FONT_WIDTHS or -Diem.fontWidths pointing to a generated font width table. See docs/TESTING.md.");
        try (var lines = Files.lines(Path.of(path))) {
            if (lines.noneMatch(line -> !line.isBlank() && !line.startsWith("#")))
                throw new IllegalStateException("Font width table has no data: " + path);
        }
        FontWidths.install(Path.of(path));
        if (!FontWidths.isLoaded()) throw new IllegalStateException("Font width table was not installed");
    }
}
