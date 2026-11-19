package com.lunar_prototype.impossbleEscapeMC.modules.market;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** 出品は全件を検査してから読み、保存は一時ファイルと直前の控えを使う。 */
final class MarketStorage {
    private final Path file;
    private final Gson gson;
    MarketStorage(Path file, Gson gson) { this.file = file; this.gson = gson; }

    List<MarketListing> load() throws IOException {
        if (!Files.exists(file)) return List.of();
        try { return read(file); }
        catch (IOException failure) {
            Path backup = file.resolveSibling(file.getFileName() + ".bak");
            if (!Files.isRegularFile(backup)) throw failure;
            List<MarketListing> recovered = read(backup);
            Files.copy(file, file.resolveSibling(file.getFileName() + ".corrupt-" + System.nanoTime()));
            return recovered;
        }
    }

    private List<MarketListing> read(Path path) throws IOException {
        try {
            List<MarketListing> values = gson.fromJson(Files.readString(path), new TypeToken<List<MarketListing>>() {}.getType());
            if (values == null) throw new IOException("出品データが空です");
            Set<UUID> ids = new HashSet<>();
            for (MarketListing listing : values) {
                if (listing == null || listing.getId() == null || listing.getSellerUuid() == null
                        || listing.getItemBase64() == null || listing.getItemBase64().isBlank()
                        || !Double.isFinite(listing.getPrice()) || listing.getPrice() <= 0 || !ids.add(listing.getId()))
                    throw new IOException("不正な出品データです");
            }
            return values;
        } catch (RuntimeException e) { throw new IOException("出品データを解析できません", e); }
    }

    void save(Collection<MarketListing> values) throws IOException {
        String json = gson.toJson(new ArrayList<>(values));
        Files.createDirectories(file.toAbsolutePath().getParent());
        Path temp = file.resolveSibling(file.getFileName() + ".tmp");
        try {
            Files.writeString(temp, json);
            if (Files.isRegularFile(file)) {
                // 読める本体だけを控えにする。破損本体から復旧した控えを潰さない。
                try { read(file); Files.copy(file, file.resolveSibling(file.getFileName() + ".bak"), StandardCopyOption.REPLACE_EXISTING); }
                catch (IOException ignored) { }
            }
            try { Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException e) { Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temp); }
    }
}
