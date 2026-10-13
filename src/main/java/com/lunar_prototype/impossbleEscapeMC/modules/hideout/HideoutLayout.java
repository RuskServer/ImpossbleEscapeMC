package com.lunar_prototype.impossbleEscapeMC.modules.hideout;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * 隠れ家の建築データ (プラグインのフォルダーの hideout/)。座標はすべてベースの原点 (保存した範囲の最小の角) から。
 * <ul>
 *   <li>base.schem / base.json: ベース (範囲のブロックをすべて) と、立つ位置・大きさ・エンティティ</li>
 *   <li>layers/&lt;設備&gt;_&lt;レベル&gt;.schem / .json: そのレベルで変わるブロック (変わらないマスは structure_void) と、
 *       増えたエンティティ・消えたエンティティ (キー)。ブロックが変わらないレベルには .schem が無い</li>
 *   <li>layout.json: 版。ベースか差分を保存するたびに上がり、建てた時の版と違う隠れ家は次に入った時に建て直す</li>
 *   <li>editor/&lt;設備&gt;_&lt;レベル&gt;.ref.schem / .ref.json: エディターで編集を始めた時の状態 (差分はこれと比べる)</li>
 *   <li>cameras.json: 隠れ家の画面で設備を映すカメラの位置と向き (設備ごと。/hideout camera で記録。建物は変わらないので版は上げない)</li>
 * </ul>
 */
public final class HideoutLayout {

    /**
     * エンティティ 1 体。位置はベースの原点から、snbt は位置と隠れ家のキーを除いた中身。
     * Gson で読み書きするので record にしない (古い Gson は record を読めない)
     */
    public static final class EntityEntry {
        private String key;
        private String snbt;
        private double x;
        private double y;
        private double z;
        private float yaw;
        private float pitch;

        private EntityEntry() {
        }

        public EntityEntry(String key, String snbt, double x, double y, double z, float yaw, float pitch) {
            this.key = key;
            this.snbt = snbt;
            this.x = x;
            this.y = y;
            this.z = z;
            this.yaw = yaw;
            this.pitch = pitch;
        }

        public String key() {
            return key;
        }

        public String snbt() {
            return snbt;
        }

        public double x() {
            return x;
        }

        public double y() {
            return y;
        }

        public double z() {
            return z;
        }

        public float yaw() {
            return yaw;
        }

        public float pitch() {
            return pitch;
        }
    }

    /** ベースの情報 */
    public static final class Base {
        public int sizeX;
        public int sizeY;
        public int sizeZ;
        public double spawnX;
        public double spawnY;
        public double spawnZ;
        public float spawnYaw;
        public float spawnPitch;
        public List<EntityEntry> entities = new ArrayList<>();
    }

    /** 差分のエンティティ */
    public static final class Layer {
        public List<EntityEntry> added = new ArrayList<>();
        public List<String> removed = new ArrayList<>();
        /**
         * ブロックを置いたマス (空気以外。ベースの原点から x, y, z)。どの設備のブロックかの判定とカメラに使う。
         * 前の版で保存した差分には無い (null。保存し直すと書かれる)
         */
        public List<int[]> placed;
    }

    /** エディターで編集を始めた時の状態のエンティティと、その時の版 */
    public static final class Reference {
        public int version;
        public List<EntityEntry> entities = new ArrayList<>();
    }

    private static final class Version {
        int version;
    }

    /** カメラの位置 (目の位置。ベースの原点から) と向き */
    public static final class CameraPose {
        public double x;
        public double y;
        public double z;
        public float yaw;
        public float pitch;
    }

    private static final class Cameras {
        java.util.Map<String, CameraPose> poses = new java.util.HashMap<>();
    }

    private final File folder;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public HideoutLayout(File pluginFolder) {
        this.folder = new File(pluginFolder, "hideout");
    }

    public File baseSchematic() {
        return new File(folder, "base.schem");
    }

    public File layerSchematic(HideoutFacility facility, int level) {
        return new File(folder, "layers/" + facility.id + "_" + level + ".schem");
    }

    public File referenceSchematic(HideoutFacility facility, int level) {
        return new File(folder, "editor/" + facility.id + "_" + level + ".ref.schem");
    }

    private File baseJson() {
        return new File(folder, "base.json");
    }

    private File layerJson(HideoutFacility facility, int level) {
        return new File(folder, "layers/" + facility.id + "_" + level + ".json");
    }

    private File referenceJson(HideoutFacility facility, int level) {
        return new File(folder, "editor/" + facility.id + "_" + level + ".ref.json");
    }

    // --- ベース ---

    public Base base() {
        return read(baseJson(), Base.class);
    }

    public void saveBase(Base base) throws IOException {
        write(baseJson(), base);
        bumpVersion();
    }

    // --- 差分 ---

    public boolean hasLayer(HideoutFacility facility, int level) {
        return layerJson(facility, level).isFile();
    }

    public Layer layer(HideoutFacility facility, int level) {
        return read(layerJson(facility, level), Layer.class);
    }

    public void saveLayer(HideoutFacility facility, int level, Layer layer) throws IOException {
        write(layerJson(facility, level), layer);
        bumpVersion();
    }

    /** 作ってある一番上のレベル (Lv.1 から続いている分。無ければ 0) */
    public int highestLevel(HideoutFacility facility) {
        int level = 0;
        while (level < facility.editLimit() && hasLayer(facility, level + 1)) level++;
        return level;
    }

    // --- エディターの基準 ---

    public Reference reference(HideoutFacility facility, int level) {
        return read(referenceJson(facility, level), Reference.class);
    }

    public void saveReference(HideoutFacility facility, int level, Reference reference) throws IOException {
        write(referenceJson(facility, level), reference);
    }

    // --- カメラ ---

    /** 記録したカメラ (無ければ null: 自動で決める) */
    public CameraPose camera(HideoutFacility facility) {
        Cameras cameras = read(new File(folder, "cameras.json"), Cameras.class);
        return cameras != null && cameras.poses != null ? cameras.poses.get(facility.id) : null;
    }

    public void saveCamera(HideoutFacility facility, CameraPose pose) throws IOException {
        Cameras cameras = read(new File(folder, "cameras.json"), Cameras.class);
        if (cameras == null || cameras.poses == null) cameras = new Cameras();
        cameras.poses.put(facility.id, pose);
        write(new File(folder, "cameras.json"), cameras);
    }

    // --- 版 ---

    public int version() {
        Version v = read(new File(folder, "layout.json"), Version.class);
        return v != null ? v.version : 0;
    }

    private void bumpVersion() throws IOException {
        Version v = new Version();
        v.version = version() + 1;
        write(new File(folder, "layout.json"), v);
    }

    private <T> T read(File file, Class<T> type) {
        if (!file.isFile()) return null;
        try {
            return gson.fromJson(Files.readString(file.toPath(), StandardCharsets.UTF_8), type);
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    private void write(File file, Object value) throws IOException {
        File parent = file.getParentFile();
        if (parent != null) parent.mkdirs();
        Files.writeString(file.toPath(), gson.toJson(value), StandardCharsets.UTF_8);
    }
}
