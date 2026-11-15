package com.lunar_prototype.impossbleEscapeMC.testing;

import java.io.IOException;
import java.nio.file.*;

/** プレビューは明示された時だけ、クラス別のファイルへ書き出す。通常のテストはファイルを作らない。 */
public final class PreviewOutput {
    private PreviewOutput() { }
    public static String path(Class<?> owner) throws IOException {
        String requested = System.getProperty("iem.previewDirectory");
        if (requested == null || requested.isBlank()) return null;
        Path directory = Path.of(requested).toAbsolutePath().normalize();
        if (!directory.startsWith(Path.of("local").toAbsolutePath().normalize()))
            throw new IllegalArgumentException("Preview output must be under local/");
        Files.createDirectories(directory);
        return directory.resolve(owner.getSimpleName() + ".json").toString();
    }
}
