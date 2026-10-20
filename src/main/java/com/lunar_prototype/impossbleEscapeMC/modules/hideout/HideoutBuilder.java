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
import com.sk89q.worldedit.function.mask.BlockTypeMask;
import com.sk89q.worldedit.function.mask.Masks;
import com.sk89q.worldedit.function.operation.ForwardExtentCopy;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.session.ClipboardHolder;
import com.sk89q.worldedit.session.PasteBuilder;
import com.sk89q.worldedit.world.block.BaseBlock;
import com.sk89q.worldedit.world.block.BlockTypes;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntitySnapshot;
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.ToIntFunction;
import java.util.logging.Logger;

/**
 * 隠れ家の建築を建てる・差分を取る ({@link HideoutLayout} のデータで)。ブロックは FAWE、エンティティは Bukkit の EntitySnapshot (SNBT)。
 * <ul>
 *   <li>建てる: 範囲 (ベースの範囲を {@link #MARGIN} だけ広げた箱) を空気にし、隠れ家のエンティティを消してから、
 *       ベース → ベースの建築改良 → 各設備 ({@link HideoutFacility} の並び) の順に、レベル 1 から重ねる</li>
 *   <li>差分を貼る: structure_void のマスは飛ばす (変わらない所)。消えたエンティティをキーで消し、増えたエンティティを置く</li>
 *   <li>隠れ家が置いたエンティティには、キーを付けたスコアボードタグ ({@link #TAG_PREFIX}) を付ける (後の差分で消せるように)</li>
 *   <li>エディター: 設備・レベルごとの区画 (エディターのワールド) に「ベース + ほかの設備は作ってある最大レベル + その設備の 1 つ前まで」を建て、
 *       その状態を覚える。保存する時は、覚えた状態と比べて変わったブロック・エンティティだけを差分にする</li>
 * </ul>
 * ブロックとエンティティの操作はメインスレッドで行う。
 */
public final class HideoutBuilder {

    /** 隠れ家が置いたエンティティの印 (スコアボードタグ)。後ろにキー */
    static final String TAG_PREFIX = "iem_hideout:";
    /** ベースの範囲の外にも建てられるよう、比べる・片付ける範囲を広げる幅 (ブロック) */
    static final int MARGIN = 8;
    /** エディターの区画の間隔 (ブロック) */
    static final int EDITOR_STRIDE = 512;
    static final int EDITOR_Y = 64;
    /**
     * 保存する時に、編集を始めた時の状態を貼って比べる区画 (エディターのワールド。どの区画とも重ならない所)。
     * .schem を読み戻してブロックを 1 マスずつ取ると、FAWE の版によって位置がずれる (貼るのは正しい) ので、貼った物をワールドから読む
     */
    private static final BlockVector3 COMPARE_ORIGIN = BlockVector3.at(-EDITOR_STRIDE, EDITOR_Y, 0);

    private final HideoutLayout layout;
    private final Logger logger;
    /** 差分ごとの、ブロックを置いたマス (空気と structure_void 以外。原点から)。建築データの版が変わったら読み直す */
    private final Map<String, Set<BlockVector3>> placedCache = new HashMap<>();
    private int placedCacheVersion = -1;

    public HideoutBuilder(HideoutLayout layout, Logger logger) {
        this.layout = layout;
        this.logger = logger;
    }

    /** 差分を保存した結果 */
    public record SaveResult(int blocks, int addedEntities, int removedEntities) {
    }

    // ---- 建てる ----

    /** 原点 origin (ベースの最小の角が来る所) に、levels のレベルで建て直す */
    public void build(World world, BlockVector3 origin, ToIntFunction<HideoutFacility> levels) throws IOException {
        HideoutLayout.Base base = requireBase();
        loadChunks(world, origin, base);
        // エンティティを先に消す (支えのブロックを先に消すと、額縁などがアイテムになって落ちる)
        removeEntities(world, area(origin, base), null);
        try (EditSession session = session(world)) {
            session.setBlocks((Region) area(world, origin, base), (com.sk89q.worldedit.function.pattern.Pattern) BlockTypes.AIR.getDefaultState());
        } catch (com.sk89q.worldedit.WorldEditException e) {
            throw new IOException(e);
        }
        paste(world, origin, layout.baseSchematic(), false);
        spawn(world, origin, base.entities);
        for (HideoutFacility facility : HideoutFacility.values()) {
            int level = Math.min(levels.applyAsInt(facility), layout.highestLevel(facility));
            for (int l = 1; l <= level; l++) applyLayer(world, origin, base, facility, l);
        }
    }

    /** 建っている隠れ家に、設備 1 レベル分の差分を重ねる */
    public void applyLayer(World world, BlockVector3 origin, HideoutFacility facility, int level) throws IOException {
        HideoutLayout.Base base = requireBase();
        loadChunks(world, origin, base);
        applyLayer(world, origin, base, facility, level);
    }

    private void applyLayer(World world, BlockVector3 origin, HideoutLayout.Base base, HideoutFacility facility, int level) throws IOException {
        HideoutLayout.Layer layer = layout.layer(facility, level);
        // 消えるエンティティを先に消す (支えのブロックが変わる前に)
        if (layer != null && !layer.removed.isEmpty()) removeEntities(world, area(origin, base), new HashSet<>(layer.removed));
        File schematic = layout.layerSchematic(facility, level);
        if (schematic.isFile()) {
            logger.fine("hideout: " + facility.id + " Lv." + level + " を " + schematic.getName() + " から貼ります");
            paste(world, origin, schematic, true);
        } else if (layer != null && layer.placed != null && !layer.placed.isEmpty()) {
            logger.warning("hideout: " + facility.id + " Lv." + level + " は置いた記録が " + layer.placed.size()
                    + " マスありますが " + schematic.getName() + " がありません");
        }
        if (layer != null) spawn(world, origin, layer.added);
    }

    // ---- どの設備のブロックか ----

    /**
     * 原点からの位置 (x, y, z) のブロックを置いた設備。levels のレベルまでの差分を重ねる順に見て、最後にそのマスへブロックを置いた物。
     * ベースのブロックなら null (建物の改良のブロックなら BASE)
     */
    public HideoutFacility ownerAt(ToIntFunction<HideoutFacility> levels, int x, int y, int z) {
        int version = layout.version();
        if (version != placedCacheVersion) {
            placedCache.clear();
            placedCacheVersion = version;
        }
        BlockVector3 position = BlockVector3.at(x, y, z);
        HideoutFacility owner = null;
        for (HideoutFacility facility : HideoutFacility.values()) {
            int level = Math.min(levels.applyAsInt(facility), layout.highestLevel(facility));
            for (int l = 1; l <= level; l++) {
                if (placed(facility, l).contains(position)) owner = facility;
            }
        }
        return owner;
    }

    /** 設備の Lv.1 から level までの差分がブロックを置いたマス (原点から)。カメラで映す所を決めるのに使う */
    public Set<BlockVector3> placedBlocks(HideoutFacility facility, int level) {
        int version = layout.version();
        if (version != placedCacheVersion) {
            placedCache.clear();
            placedCacheVersion = version;
        }
        Set<BlockVector3> all = new HashSet<>();
        for (int l = 1; l <= Math.min(level, layout.highestLevel(facility)); l++) all.addAll(placed(facility, l));
        return all;
    }

    private Set<BlockVector3> placed(HideoutFacility facility, int level) {
        return placedCache.computeIfAbsent(facility.id + "_" + level, key -> {
            Set<BlockVector3> positions = new HashSet<>();
            // 保存した時に書いておいたマス
            HideoutLayout.Layer layer = layout.layer(facility, level);
            if (layer != null && layer.placed != null) {
                for (int[] p : layer.placed) positions.add(BlockVector3.at(p[0], p[1], p[2]));
            }
            // 前の版の差分は置いたマスを書いていない (.schem の読み戻しは位置がずれるので使わない)。保存し直すと書かれる
            return positions;
        });
    }

    // ---- ベースの保存 ----

    /**
     * WorldEdit で選んだ範囲をベースとして保存する。原点は範囲の最小の角、立つ位置は保存した人の位置。
     * 範囲のエンティティ (プレイヤー以外) もベースに入れる
     */
    public HideoutLayout.Base saveBase(Player player) throws Exception {
        com.sk89q.worldedit.world.World weWorld = BukkitAdapter.adapt(player.getWorld());
        Region selection = WorldEdit.getInstance().getSessionManager().get(BukkitAdapter.adapt(player)).getSelection(weWorld);
        BlockVector3 min = selection.getMinimumPoint();
        BlockVector3 max = selection.getMaximumPoint();
        CuboidRegion region = new CuboidRegion(weWorld, min, max);
        copyToFile(player.getWorld(), region, min, layout.baseSchematic());

        HideoutLayout.Base base = new HideoutLayout.Base();
        base.sizeX = max.x() - min.x() + 1;
        base.sizeY = max.y() - min.y() + 1;
        base.sizeZ = max.z() - min.z() + 1;
        Location at = player.getLocation();
        base.spawnX = at.getX() - min.x();
        base.spawnY = at.getY() - min.y();
        base.spawnZ = at.getZ() - min.z();
        base.spawnYaw = at.getYaw();
        base.spawnPitch = at.getPitch();
        BoundingBox box = new BoundingBox(min.x(), min.y(), min.z(), max.x() + 1, max.y() + 1, max.z() + 1);
        int n = 0;
        for (HideoutLayout.EntityEntry entry : capture(player.getWorld(), min, box)) {
            base.entities.add(withKey(entry, "base_" + n++));
        }
        layout.saveBase(base);
        return base;
    }

    // ---- エディター ----

    /** 設備・レベルごとのエディターの区画の原点 */
    public static BlockVector3 editorOrigin(HideoutFacility facility, int level) {
        return BlockVector3.at(facility.ordinal() * EDITOR_STRIDE, EDITOR_Y, level * EDITOR_STRIDE);
    }

    /**
     * 編集を始める: 区画に「ベース + ほかの設備は作ってある最大レベル + その設備の 1 つ前まで」を建て、その状態を覚える。
     * 建物の Lv.0 (ベースそのもの) はベースだけを建てる (ほかを並べると、保存した時にベースに入ってしまう)
     */
    public void prepareEditor(World world, HideoutFacility target, int level) throws IOException {
        HideoutLayout.Base base = requireBase();
        BlockVector3 origin = editorOrigin(target, level);
        if (isBaseItself(target, level)) {
            build(world, origin, facility -> 0);
        } else {
            build(world, origin, facility -> facility == target ? level - 1 : layout.highestLevel(facility));
        }
        CuboidRegion region = area(world, origin, base);
        copyToFile(world, region, origin, layout.referenceSchematic(target, level));
        HideoutLayout.Reference reference = new HideoutLayout.Reference();
        reference.version = layout.version();
        reference.entities = capture(world, origin, area(origin, base));
        layout.saveReference(target, level, reference);
    }

    /** 建物の Lv.0 = ベースそのもの */
    public static boolean isBaseItself(HideoutFacility facility, int level) {
        return facility == HideoutFacility.BASE && level == 0;
    }

    /**
     * 建物の Lv.0 の区画で直したベースを保存する。範囲・大きさ・立つ位置は今のベースのまま (広げる時は /hideout base save)。
     * エンティティは今のキーを保つ (後のレベルが消すエンティティをキーで指しているため)。キーの無い物には新しいキーを付ける
     */
    public HideoutLayout.Base saveBaseFromEditor(World world) throws IOException {
        HideoutLayout.Base base = requireBase();
        HideoutLayout.Reference reference = layout.reference(HideoutFacility.BASE, 0);
        if (reference != null && reference.version != layout.version()) {
            throw new IOException("編集を始めた後にベースかほかの差分が保存されています。/hideout edit base 0 reset で区画を作り直してください");
        }
        BlockVector3 origin = editorOrigin(HideoutFacility.BASE, 0);
        loadChunks(world, origin, base);
        CuboidRegion region = new CuboidRegion(BukkitAdapter.adapt(world), origin,
                origin.add(base.sizeX - 1, base.sizeY - 1, base.sizeZ - 1));
        copyToFile(world, region, origin, layout.baseSchematic());
        BoundingBox box = new BoundingBox(origin.x(), origin.y(), origin.z(),
                origin.x() + base.sizeX, origin.y() + base.sizeY, origin.z() + base.sizeZ);
        List<HideoutLayout.EntityEntry> captured = capture(world, origin, box);
        Set<String> used = new HashSet<>();
        for (HideoutLayout.EntityEntry entry : captured) {
            if (entry.key() != null) used.add(entry.key());
        }
        base.entities = new ArrayList<>();
        int n = 0;
        for (HideoutLayout.EntityEntry entry : captured) {
            if (entry.key() != null) {
                base.entities.add(entry);
                continue;
            }
            while (used.contains("base_" + n)) n++;
            used.add("base_" + n);
            base.entities.add(withKey(entry, "base_" + n));
        }
        layout.saveBase(base);
        return base;
    }

    /** 編集した区画を、編集を始めた時の状態と比べて差分として保存する */
    public SaveResult saveLayer(World world, HideoutFacility target, int level) throws IOException {
        return saveLayer(world, target, level, false);
    }

    /**
     * @param force 区画の大半が変わっていても保存する (参照がずれている時の誤爆を防ぐため、普段は拒否する)
     */
    public SaveResult saveLayer(World world, HideoutFacility target, int level, boolean force) throws IOException {
        HideoutLayout.Base base = requireBase();
        HideoutLayout.Reference reference = layout.reference(target, level);
        File referenceFile = layout.referenceSchematic(target, level);
        if (reference == null || !referenceFile.isFile()) throw new IOException("先に /hideout edit " + target.id + " " + level + " で編集を始めてください");
        if (reference.version != layout.version()) {
            throw new IOException("編集を始めた後にベースかほかの差分が保存されています。/hideout edit " + target.id + " "
                    + level + " reset で区画を作り直してください");
        }
        BlockVector3 origin = editorOrigin(target, level);
        loadChunks(world, origin, base);
        // 編集を始めた時の状態を、比べる区画に貼る
        loadChunks(world, COMPARE_ORIGIN, base);
        paste(world, COMPARE_ORIGIN, referenceFile, false);

        // ブロック: 変わったマスと、その今のブロック
        Map<BlockVector3, BaseBlock> changed = new HashMap<>();
        try (EditSession session = session(world)) {
            for (int x = -MARGIN; x < base.sizeX + MARGIN; x++) {
                for (int y = -MARGIN; y < base.sizeY + MARGIN; y++) {
                    for (int z = -MARGIN; z < base.sizeZ + MARGIN; z++) {
                        BaseBlock now = session.getFullBlock(origin.add(x, y, z));
                        BaseBlock old = session.getFullBlock(COMPARE_ORIGIN.add(x, y, z));
                        if (!sameBlock(now, old)) changed.put(BlockVector3.at(x, y, z), now);
                    }
                }
            }
        }
        long volume = (long) (base.sizeX + 2 * MARGIN) * (base.sizeY + 2 * MARGIN) * (base.sizeZ + 2 * MARGIN);
        if (!force && changed.size() * 5 > volume * 3) {
            throw new IOException("区画の " + (changed.size() * 100 / Math.max(1L, volume))
                    + "% が変わっています (参照がずれている可能性)。意図した変更なら /hideout save "
                    + target.id + " " + level + " force");
        }
        File schematic = layout.layerSchematic(target, level);

        HideoutLayout.Layer layer = new HideoutLayout.Layer();
        layer.placed = new ArrayList<>();
        for (Map.Entry<BlockVector3, BaseBlock> e : changed.entrySet()) {
            if (!e.getValue().getBlockType().getMaterial().isAir()) {
                layer.placed.add(new int[]{e.getKey().x(), e.getKey().y(), e.getKey().z()});
            }
        }

        // エンティティ: キーが同じで中身も位置も同じ物は変わっていない。それ以外は、前の物を消して今の物を置く
        Map<String, HideoutLayout.EntityEntry> old = new HashMap<>();
        for (HideoutLayout.EntityEntry entry : reference.entities) {
            if (entry.key() != null) old.put(entry.key(), entry);
        }
        Set<String> kept = new HashSet<>();
        int n = 0;
        for (HideoutLayout.EntityEntry now : capture(world, origin, area(origin, base))) {
            HideoutLayout.EntityEntry was = now.key() != null ? old.get(now.key()) : null;
            if (was != null && sameEntity(was, now)) {
                kept.add(now.key());
                continue;
            }
            layer.added.add(withKey(now, target.id + "_" + level + "_" + n++));
        }
        for (String key : old.keySet()) {
            if (!kept.contains(key)) layer.removed.add(key);
        }

        // 何も変わっていないレベルは作らない (空の差分が「作ってあるレベル」として数えられるのを防ぐ)
        if (changed.isEmpty() && layer.added.isEmpty() && layer.removed.isEmpty()) {
            layout.deleteLayer(target, level);
            logger.info("hideout: " + target.id + " Lv." + level + " は変更が無いので差分を消しました");
            return new SaveResult(0, 0, 0);
        }
        if (changed.isEmpty()) {
            if (schematic.isFile() && !schematic.delete()) throw new IOException("古い差分を消せませんでした: " + schematic);
        } else {
            writeDiff(world, origin, changed, schematic);
        }
        layout.saveLayer(target, level, layer);
        logger.info("hideout: " + target.id + " Lv." + level + " の差分を保存: ブロック " + changed.size()
                + " / エンティティ +" + layer.added.size() + " -" + layer.removed.size());
        return new SaveResult(changed.size(), layer.added.size(), layer.removed.size());
    }

    // ---- ブロック ----

    private static boolean sameBlock(BaseBlock a, BaseBlock b) {
        if (!a.hasNbtData() && !b.hasNbtData()) return a.toImmutableState().equals(b.toImmutableState());
        // NBT の付いたブロック (チェスト・看板など) は中身まで比べる。置いた場所の座標 (いちばん外側の x, y, z) は除く
        return SnbtPosition.strip(a.toString()).equals(SnbtPosition.strip(b.toString()));
    }

    /** 変わったマスだけのクリップボード (変わらないマスは structure_void) を書く。原点はベースの原点 */
    private void writeDiff(World world, BlockVector3 origin, Map<BlockVector3, BaseBlock> changed, File file) throws IOException {
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (BlockVector3 p : changed.keySet()) {
            minX = Math.min(minX, p.x());
            minY = Math.min(minY, p.y());
            minZ = Math.min(minZ, p.z());
            maxX = Math.max(maxX, p.x());
            maxY = Math.max(maxY, p.y());
            maxZ = Math.max(maxZ, p.z());
        }
        CuboidRegion region = new CuboidRegion(BukkitAdapter.adapt(world),
                origin.add(minX, minY, minZ), origin.add(maxX, maxY, maxZ));
        try (Clipboard diff = new BlockArrayClipboard(region)) {
            diff.setOrigin(origin);
            for (BlockVector3 p : region) diff.setBlock(p, BlockTypes.STRUCTURE_VOID.getDefaultState().toBaseBlock());
            for (Map.Entry<BlockVector3, BaseBlock> e : changed.entrySet()) diff.setBlock(origin.add(e.getKey()), e.getValue());
            write(diff, file);
        } catch (com.sk89q.worldedit.WorldEditException e) {
            throw new IOException(e);
        }
    }

    /** 範囲のブロックをクリップボードにして書く (原点を origin に) */
    private void copyToFile(World world, CuboidRegion region, BlockVector3 origin, File file) throws IOException {
        try (EditSession session = session(world); Clipboard clipboard = new BlockArrayClipboard(region)) {
            clipboard.setOrigin(origin);
            ForwardExtentCopy copy = new ForwardExtentCopy(session, region, clipboard, region.getMinimumPoint());
            copy.setCopyingEntities(false);
            Operations.complete(copy);
            write(clipboard, file);
        } catch (com.sk89q.worldedit.WorldEditException e) {
            throw new IOException(e);
        }
    }

    /** クリップボードの原点を origin に合わせて貼る。差分なら structure_void のマスは飛ばす */
    private void paste(World world, BlockVector3 origin, File file, boolean diff) throws IOException {
        try (Clipboard clipboard = read(file); EditSession session = session(world)) {
            PasteBuilder paste = new ClipboardHolder(clipboard).createPaste(session).to(origin)
                    .ignoreAirBlocks(false).copyEntities(false).copyBiomes(false);
            if (diff) paste.maskSource(Masks.negate(new BlockTypeMask(clipboard, BlockTypes.STRUCTURE_VOID)));
            Operations.complete(paste.build());
        } catch (com.sk89q.worldedit.WorldEditException e) {
            throw new IOException(e);
        }
    }

    private static Clipboard read(File file) throws IOException {
        ClipboardFormat format = ClipboardFormats.findByFile(file);
        if (format == null) throw new IOException("読めない形式: " + file);
        try (InputStream in = new FileInputStream(file); ClipboardReader reader = format.getReader(in)) {
            return reader.read();
        }
    }

    private static void write(Clipboard clipboard, File file) throws IOException {
        File parent = file.getParentFile();
        if (parent != null) parent.mkdirs();
        try (FileOutputStream out = new FileOutputStream(file);
             ClipboardWriter writer = BuiltInClipboardFormat.SPONGE_SCHEMATIC.getWriter(out)) {
            writer.write(clipboard);
        }
    }

    private static EditSession session(World world) {
        return WorldEdit.getInstance().newEditSessionBuilder().world(BukkitAdapter.adapt(world)).limitUnlimited().build();
    }

    // ---- エンティティ ----

    /** 箱の中のエンティティ (プレイヤー以外)。位置は原点から、キーは隠れ家のタグから (無ければ null) */
    private List<HideoutLayout.EntityEntry> capture(World world, BlockVector3 origin, BoundingBox box) {
        List<HideoutLayout.EntityEntry> entries = new ArrayList<>();
        for (Entity entity : world.getNearbyEntities(box)) {
            if (entity instanceof Player) continue;
            String key = null;
            List<String> tags = new ArrayList<>();
            for (String tag : entity.getScoreboardTags()) {
                if (tag.startsWith(TAG_PREFIX)) {
                    tags.add(tag);
                    key = tag.substring(TAG_PREFIX.length());
                }
            }
            // 中身はキーのタグを外して写す (置き直した時に、前のキーで消されないように)
            tags.forEach(entity::removeScoreboardTag);
            EntitySnapshot snapshot = entity.createSnapshot();
            tags.forEach(entity::addScoreboardTag);
            if (snapshot == null) continue;
            Location at = entity.getLocation();
            entries.add(new HideoutLayout.EntityEntry(key, snapshot.getAsString(),
                    at.getX() - origin.x(), at.getY() - origin.y(), at.getZ() - origin.z(), at.getYaw(), at.getPitch()));
        }
        return entries;
    }

    private void spawn(World world, BlockVector3 origin, List<HideoutLayout.EntityEntry> entries) {
        for (HideoutLayout.EntityEntry entry : entries) {
            try {
                EntitySnapshot snapshot = Bukkit.getEntityFactory().createEntitySnapshot(entry.snbt());
                Location at = new Location(world, origin.x() + entry.x(), origin.y() + entry.y(), origin.z() + entry.z(),
                        entry.yaw(), entry.pitch());
                Entity entity = snapshot.createEntity(at);
                entity.addScoreboardTag(TAG_PREFIX + entry.key());
            } catch (RuntimeException e) {
                logger.warning("隠れ家のエンティティ " + entry.key() + " を置けませんでした: " + e.getMessage());
            }
        }
    }

    /** 箱の中の、隠れ家が置いたエンティティを消す (keys が null なら全部) */
    private static void removeEntities(World world, BoundingBox box, Set<String> keys) {
        for (Entity entity : world.getNearbyEntities(box)) {
            if (entity instanceof Player) continue;
            for (String tag : entity.getScoreboardTags()) {
                if (tag.startsWith(TAG_PREFIX) && (keys == null || keys.contains(tag.substring(TAG_PREFIX.length())))) {
                    entity.remove();
                    break;
                }
            }
        }
    }

    private static boolean sameEntity(HideoutLayout.EntityEntry a, HideoutLayout.EntityEntry b) {
        return a.snbt().equals(b.snbt()) && Math.abs(a.x() - b.x()) < 1e-3 && Math.abs(a.y() - b.y()) < 1e-3
                && Math.abs(a.z() - b.z()) < 1e-3 && Math.abs(a.yaw() - b.yaw()) < 0.01f && Math.abs(a.pitch() - b.pitch()) < 0.01f;
    }

    private static HideoutLayout.EntityEntry withKey(HideoutLayout.EntityEntry entry, String key) {
        return new HideoutLayout.EntityEntry(key, entry.snbt(), entry.x(), entry.y(), entry.z(), entry.yaw(), entry.pitch());
    }

    // ---- 範囲 ----

    private HideoutLayout.Base requireBase() throws IOException {
        HideoutLayout.Base base = layout.base();
        if (base == null || !layout.baseSchematic().isFile()) throw new IOException("隠れ家のベースがまだ保存されていません (/hideout base save)");
        return base;
    }

    /** 片付ける・比べる範囲 (ベースの範囲を MARGIN だけ広げた箱) */
    private static CuboidRegion area(World world, BlockVector3 origin, HideoutLayout.Base base) {
        return new CuboidRegion(BukkitAdapter.adapt(world), origin.add(-MARGIN, -MARGIN, -MARGIN),
                origin.add(base.sizeX - 1 + MARGIN, base.sizeY - 1 + MARGIN, base.sizeZ - 1 + MARGIN));
    }

    private static BoundingBox area(BlockVector3 origin, HideoutLayout.Base base) {
        return new BoundingBox(origin.x() - MARGIN, origin.y() - MARGIN, origin.z() - MARGIN,
                origin.x() + base.sizeX + MARGIN, origin.y() + base.sizeY + MARGIN, origin.z() + base.sizeZ + MARGIN);
    }

    private static void loadChunks(World world, BlockVector3 origin, HideoutLayout.Base base) {
        int minX = (origin.x() - MARGIN) >> 4, maxX = (origin.x() + base.sizeX + MARGIN) >> 4;
        int minZ = (origin.z() - MARGIN) >> 4, maxZ = (origin.z() + base.sizeZ + MARGIN) >> 4;
        for (int cx = minX; cx <= maxX; cx++) {
            for (int cz = minZ; cz <= maxZ; cz++) world.getChunkAt(cx, cz);
        }
    }
}
