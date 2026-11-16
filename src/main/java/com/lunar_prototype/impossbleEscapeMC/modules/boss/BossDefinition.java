package com.lunar_prototype.impossbleEscapeMC.modules.boss;

import com.lunar_prototype.impossbleEscapeMC.ai.ScavBrain.BrainLevel;
import java.util.*;

/** 編成と装備の定義。配置やレイドの状態を持たない。 */
public record BossDefinition(String id, String name, boolean enabled, Set<String> maps,
        double chance, double guardSpawnRadius, List<Member> members) {
    public static final String DEFAULT_ID = "spectre_commander";
    public BossDefinition(String id,String name,boolean enabled,Set<String> maps,double chance,List<Member> members) {
        this(id,name,enabled,maps,chance,8,members);
    }
    public Member leader(){return members.stream().filter(Member::leader).findFirst().orElseThrow();}
    public BossDefinition {
        if (id == null || !id.matches("[a-z0-9_-]+") || name == null || name.isBlank())
            throw new IllegalArgumentException("Invalid boss id/name");
        if(!Double.isFinite(guardSpawnRadius)||guardSpawnRadius<2||guardSpawnRadius>16)throw new IllegalArgumentException("Invalid guard spawn radius");
        maps = Set.copyOf(maps); members = List.copyOf(members);
        if (members.isEmpty() || members.stream().filter(Member::leader).count() != 1
                || members.stream().map(Member::role).distinct().count() != members.size())
            throw new IllegalArgumentException("Boss needs unique roles and exactly one leader");
    }
    public record Member(String role, boolean leader, String gun, String helmet, String chest,
            String backpack, BrainLevel brain, double health, double radius, String ammo) {
        public Member {
            if (role == null || !role.matches("[a-z0-9_-]+") || gun == null || gun.isBlank() || brain == null
                    || !Double.isFinite(health) || health <= 0 || !Double.isFinite(radius) || radius <= 0
                    || ammo == null || ammo.isBlank()) throw new IllegalArgumentException("Invalid boss member");
        }
    }
}
