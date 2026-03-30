package com.lunar_prototype.impossbleEscapeMC.modules.hideout;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extent.clipboard.BlockArrayClipboard;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.BuiltInClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardWriter;
import com.sk89q.worldedit.function.operation.ForwardExtentCopy;
import com.sk89q.worldedit.function.operation.Operation;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.session.ClipboardHolder;
import org.bukkit.Location;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;

/**
 * FAWE (FastAsyncWorldEdit) API を利用して構造物を保存・配置するサービス。
 * 自動的に非空気ブロックの最小境界箱を算出し、指定したアンカー (Origin) を基準に保存する。
 */
public class SmartStructureService {
    private final HideoutModule module;

    public SmartStructureService(HideoutModule module) {
        this.module = module;
    }

    /**
     * .schem ファイルを読み込み、指定した座標 (アンカー) を基準に配置する
     * @param file      .schem ファイル
     * @param anchor    配置の基準点 (Origin) となる座標
     * @param ignoreAir シェマティック内の空気を無視して配置するかどうか
     */
    public void placeSmart(File file, Location anchor, boolean ignoreAir) {
        if (!file.exists()) {
            module.getPlugin().getLogger().warning("Schematic file not found: " + file.getPath());
            return;
        }

        ClipboardFormat format = ClipboardFormats.findByFile(file);
        if (format == null) {
            module.getPlugin().getLogger().warning("Unknown schematic format: " + file.getPath());
            return;
        }

        try (ClipboardReader reader = format.getReader(new FileInputStream(file))) {
            Clipboard clipboard = reader.read();

            com.sk89q.worldedit.world.World weWorld = BukkitAdapter.adapt(anchor.getWorld());
            BlockVector3 to = BlockVector3.at(anchor.getX(), anchor.getY(), anchor.getZ());

            try (EditSession editSession = WorldEdit.getInstance().newEditSessionBuilder()
                    .world(weWorld)
                    .build()) {
                
                Operation operation = new ClipboardHolder(clipboard)
                        .createPaste(editSession)
                        .to(to)
                        .ignoreAirBlocks(ignoreAir)
                        .build();

                Operations.complete(operation);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 指定された広域範囲から非空気ブロックを見つけ出し、最小バウンディングボックスを計算して保存する
     * @param file   保存先 (.schem ファイル)
     * @param anchor 構造物の起点（Origin）となる座標
     * @param min    探索範囲の Point 1
     * @param max    探索範囲の Point 2
     */
    public void saveOptimizedSchematic(File file, Location anchor, Location min, Location max) {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }

        com.sk89q.worldedit.world.World weWorld = BukkitAdapter.adapt(anchor.getWorld());
        BlockVector3 minVec = BlockVector3.at(min.getBlockX(), min.getBlockY(), min.getBlockZ());
        BlockVector3 maxVec = BlockVector3.at(max.getBlockX(), max.getBlockY(), max.getBlockZ());
        
        CuboidRegion fullRegion = new CuboidRegion(weWorld, minVec, maxVec);
        
        // 広範囲の処理になるため非同期で実行
        module.getPlugin().getServer().getScheduler().runTaskAsynchronously(module.getPlugin(), () -> {
            try {
                int outMinX = Integer.MAX_VALUE;
                int outMinY = Integer.MAX_VALUE;
                int outMinZ = Integer.MAX_VALUE;
                int outMaxX = Integer.MIN_VALUE;
                int outMaxY = Integer.MIN_VALUE;
                int outMaxZ = Integer.MIN_VALUE;
                boolean foundAny = false;

                try (EditSession editSession = WorldEdit.getInstance().newEditSession(weWorld)) {
                    // 最適化: バウンディングボックスの計算 (非空気ブロックのみ)
                    for (BlockVector3 pt : fullRegion) {
                        if (!editSession.getBlock(pt).getBlockType().getMaterial().isAir()) {
                            if (pt.getX() < outMinX) outMinX = pt.getX();
                            if (pt.getY() < outMinY) outMinY = pt.getY();
                            if (pt.getZ() < outMinZ) outMinZ = pt.getZ();
                            if (pt.getX() > outMaxX) outMaxX = pt.getX();
                            if (pt.getY() > outMaxY) outMaxY = pt.getY();
                            if (pt.getZ() > outMaxZ) outMaxZ = pt.getZ();
                            foundAny = true;
                        }
                    }

                    if (!foundAny) {
                        module.getPlugin().getLogger().warning("No blocks found to save in the specified region.");
                        return;
                    }

                    // バウンディングボックスに基づく最小領域の生成
                    BlockVector3 newMin = BlockVector3.at(outMinX, outMinY, outMinZ);
                    BlockVector3 newMax = BlockVector3.at(outMaxX, outMaxY, outMaxZ);
                    CuboidRegion optimizedRegion = new CuboidRegion(weWorld, newMin, newMax);

                    // クリップボードの作成とOrigin(アンカー)の設定
                    BlockArrayClipboard clipboard = new BlockArrayClipboard(optimizedRegion);
                    clipboard.setOrigin(BlockVector3.at(anchor.getX(), anchor.getY(), anchor.getZ()));

                    // 対象領域のコピーをクリップボードへ保存
                    ForwardExtentCopy forwardExtentCopy = new ForwardExtentCopy(
                            editSession, optimizedRegion, clipboard, optimizedRegion.getMinimumPoint()
                    );
                    forwardExtentCopy.setCopyingEntities(true);
                    Operations.complete(forwardExtentCopy);

                    // .schem 形式でファイルに保存
                    try (ClipboardWriter writer = BuiltInClipboardFormat.SPONGE_SCHEMATIC.getWriter(new FileOutputStream(file))) {
                        writer.write(clipboard);
                    }
                    
                    module.getPlugin().getLogger().info("Optimized schematic saved to: " + file.getPath() + " (Size: " + clipboard.getDimensions() + ")");
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }
}
