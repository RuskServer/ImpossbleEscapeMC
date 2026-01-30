package com.lunar_prototype.impossbleEscapeMC.ai;

import org.bukkit.entity.Player;

import java.util.List;

public class ScavContext {
    public EntityState entity;
    public EnvironmentState environment;
    public String last_action;
    public Personality personality;

    public static class EntityState {
        public String id;
        public int hp_pct;
        public int max_hp;
        public List<String> inventory;
        public String stance;
    }

    public static class EnvironmentState {
        public List<EnemyInfo> nearby_enemies;
        public List<AllyInfo> nearby_allies;
        public CoverInfo nearest_cover;
    }

    public static class EnemyInfo {
        public double dist;
        public String health; // "low", "mid", "high"
        public String holding;
        public boolean in_sight;
        public Player playerInstance;
    }

    public static class AllyInfo {
        public double dist;
        public String status; // "HEALTHY", "WOUNDED", "DEAD"
    }

    public static class CoverInfo {
        public double dist;
        public double safety_score;
    }

    public static class Personality {
        public double bravery;
        public double aggressiveness;
    }
}
