package com.lunar_prototype.impossbleEscapeMC.ai;

import com.lunar_prototype.dark_singularity_api.Singularity;
import com.lunar_prototype.impossbleEscapeMC.ImpossbleEscapeMC;
import org.bukkit.Bukkit;

import java.io.File;
import java.nio.file.Files;

public class BrainManager {
    private static final String MODEL_FILE_NAME = "best_brain.bin";
    private static final String FITNESS_FILE_NAME = "fitness.txt";
    private static float bestFitness = -1000f;
    private static File modelFile;
    private static File fitnessFile;

    public static void init(ImpossbleEscapeMC plugin) {
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }
        modelFile = new File(plugin.getDataFolder(), MODEL_FILE_NAME);
        fitnessFile = new File(plugin.getDataFolder(), FITNESS_FILE_NAME);
        
        // 保存された評価値を読み込む
        if (fitnessFile.exists()) {
            try {
                String content = Files.readString(fitnessFile.toPath());
                bestFitness = Float.parseFloat(content.trim());
                Bukkit.getLogger().info("[AI-Manager] Loaded best fitness: " + bestFitness);
            } catch (Exception e) {
                Bukkit.getLogger().warning("[AI-Manager] Could not load fitness file: " + e.getMessage());
            }
        }

        if (modelFile.exists()) {
            Bukkit.getLogger().info("[AI-Manager] Master model found. Ready to propagate knowledge.");
        }
    }

    public static void loadMasterModel(Singularity s) {
        if (modelFile != null && modelFile.exists()) {
            int result = s.loadModel(modelFile.getAbsolutePath());
            if (result == 0) {
                Bukkit.getLogger().info("[AI-Manager] Successfully loaded master model.");
            } else {
                Bukkit.getLogger().warning("[AI-Manager] Failed to load master model. Error code: " + result);
            }
        }
    }

    public static synchronized void reportResult(Singularity s, float fitness) {
        if (fitness > bestFitness) {
            bestFitness = fitness;
            try {
                // v1.2.0: モデルの保存 (戻り値チェック)
                int result = s.saveModel(modelFile.getAbsolutePath());
                if (result != 0) {
                    Bukkit.getLogger().warning("[AI-Manager] Failed to save model. Error code: " + result);
                    return;
                }
                
                // 評価値の保存
                Files.writeString(fitnessFile.toPath(), String.valueOf(fitness));
                
                Bukkit.getLogger().info(String.format("[AI-Manager] === NEW RECORD! === Fitness: %.2f (Model saved)", fitness));
            } catch (Exception e) {
                Bukkit.getLogger().warning("[AI-Manager] Failed to save best model or fitness: " + e.getMessage());
            }
        }
    }

    public static float getBestFitness() {
        return bestFitness;
    }
}
