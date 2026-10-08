package com.lunar_prototype.impossbleEscapeMC.ai.weapon;

import com.lunar_prototype.impossbleEscapeMC.ImpossbleEscapeMC;
import com.lunar_prototype.impossbleEscapeMC.ai.AiRaidLogger;
import com.lunar_prototype.impossbleEscapeMC.ai.DatapackGunner;
import com.lunar_prototype.impossbleEscapeMC.ai.DatapackGunnerManager;
import com.lunar_prototype.impossbleEscapeMC.ai.ScavSpawner;
import org.bukkit.entity.Mob;

import java.util.HashMap;
import java.util.Map;

/**
 * Toi's Armoryのデータパック銃。射撃はSCAV専用のFakePlayer ({@link DatapackGunner}) が行い、
 * 弾道・ダメージ・発射エフェクト・弾数はデータパックが管理する。
 * FakePlayerは最初に引き金を引いた時に作る (戦闘しないSCAVの分はデータパックのプレイヤー処理を走らせない)。
 */
public final class DatapackScavWeapon implements ScavWeapon {

    private final Mob scav;
    private final DatapackGunProfile profile;

    public DatapackScavWeapon(Mob scav, DatapackGunProfile profile) {
        this.scav = scav;
        this.profile = profile;
    }

    public DatapackGunProfile profile() { return profile; }

    @Override public String id() { return profile.id(); }
    @Override public double rpm() { return profile.rpm(); }
    @Override public boolean isAutomatic() { return profile.automatic(); }
    @Override public boolean isManualAction() { return profile.manualAction(); }
    @Override public int magazineSize() { return profile.magazineSize(); }
    @Override public double preferredRange() { return profile.preferredRange(); }

    @Override
    public int ammo() {
        DatapackGunner gunner = DatapackGunnerManager.get(scav.getUniqueId());
        // まだ撃っていなければ満タン
        return gunner != null ? gunner.ammo() : profile.magazineSize();
    }

    @Override
    public boolean isReloading() {
        DatapackGunner gunner = DatapackGunnerManager.get(scav.getUniqueId());
        return gunner != null && gunner.isReloading();
    }

    @Override
    public void fire(double inaccuracy) {
        if (!DatapackGunnerManager.getOrCreate(scav, profile).pullTrigger(inaccuracy)) return;

        // プラグイン銃 (GunListener#executeMobShoot) と同じくレイドのAIログに射撃を記録する
        String raidSessionId = ScavSpawner.getRaidSessionId(scav.getUniqueId());
        AiRaidLogger logger = ImpossbleEscapeMC.getInstance().getAiRaidLogger();
        if (raidSessionId != null && logger != null && logger.isEnabled()) {
            Map<String, Object> payload = new HashMap<>();
            payload.put("weapon", profile.id());
            payload.put("inaccuracy", inaccuracy);
            payload.put("pellets", profile.projectilesPerShot());
            payload.put("datapack", true);
            logger.logEvent(raidSessionId, scav.getUniqueId(), "SHOT_FIRED", payload);
        }
    }

    @Override
    public void release() {
        DatapackGunnerManager.remove(scav.getUniqueId());
    }
}
