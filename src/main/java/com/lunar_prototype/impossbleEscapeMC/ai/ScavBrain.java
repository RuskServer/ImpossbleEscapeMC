package com.lunar_prototype.impossbleEscapeMC.ai;

import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Rust製 'dark-singularity' と通信するSCAVの脳。
 * 感情（恐怖・興奮）と戦術決定（Q学習）を司る。
 */
public class ScavBrain {
    private final long nativeHandle;
    private final AtomicBoolean disposed = new AtomicBoolean(false);

    // 感情ニューロン (Rust側の状態と同期)
    public final LiquidNeuron aggression = new LiquidNeuron(0.5);
    public final LiquidNeuron fear = new LiquidNeuron(0.4);

    // システム状態
    public float systemTemperature = 0.5f; // 戦闘の激しさ
    public float frustration = 0.0f;       // ストレス値
    public float adrenaline = 0.0f;        // 反応速度ボーナス

    // JNIライブラリのロード
    static {
        try {
            System.loadLibrary("dark_singularity");
        } catch (UnsatisfiedLinkError e) {
            String libName = System.mapLibraryName("dark_singularity.so");
            // パスは環境に合わせて調整してください
            File lib = new File("plugins/ImpossbleEscapeMC/" + libName);
            if (lib.exists()) System.load(lib.getAbsolutePath());
        }
    }

    public ScavBrain() {
        this.nativeHandle = initNativeSingularity();
    }

    /**
     * 行動を選択する (0-7のアクションIDを返す)
     * @param inputs [0]:優位性, [1]:距離, [2]:HP率, [3]:弾薬状況, [4]:敵数
     */
    public int think(float[] inputs) {
        if (nativeHandle == 0) return 4; // Default: OBSERVE
        int actionIdx = selectActionNative(nativeHandle, inputs);
        syncFromNative(); // 感情状態の更新
        return actionIdx;
    }

    public void learn(float reward) {
        if (nativeHandle != 0) learnNative(nativeHandle, reward);
    }

    public void dispose() {
        if (nativeHandle != 0 && disposed.compareAndSet(false, true)) {
            destroyNativeSingularity(nativeHandle);
        }
    }

    // Rust側の状態をJavaフィールドへコピー
    private void syncFromNative() {
        this.systemTemperature = getSystemTemperature(nativeHandle);
        this.frustration = getFrustration(nativeHandle);
        this.adrenaline = getAdrenaline(nativeHandle);

        float[] states = getNeuronStates(nativeHandle);
        if (states != null && states.length >= 2) {
            this.aggression.setState(states[0]);
            this.fear.setState(states[1]);
        }
    }

    // --- Native Methods (lib.rsに対応) ---
    private native long initNativeSingularity();
    private native void destroyNativeSingularity(long handle);
    private native int selectActionNative(long handle, float[] inputs);
    private native void learnNative(long handle, float reward);
    private native float getSystemTemperature(long handle);
    private native float getFrustration(long handle);
    private native float getAdrenaline(long handle);
    private native float[] getNeuronStates(long handle);
}