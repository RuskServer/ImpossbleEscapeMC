package com.lunar_prototype.impossbleEscapeMC.animation.state;

import com.lunar_prototype.impossbleEscapeMC.item.AmmoDefinition;
import com.lunar_prototype.impossbleEscapeMC.item.GunStats;
import com.lunar_prototype.impossbleEscapeMC.item.ItemFactory;
import com.lunar_prototype.impossbleEscapeMC.item.ItemRegistry;
import com.lunar_prototype.impossbleEscapeMC.util.PDCKeys;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;

import java.util.List;
import java.util.Map;

/**
 * ポンプアクションショットガン専用リロードステート。
 *
 * <p>
 * フェーズ：
 * <ol>
 * <li>{@link Phase#INTRO} — reload or tactical_reload アニメーションを1回再生</li>
 * <li>{@link Phase#LOOP} — reload_loop アニメーションを1発分ずつ繰り返し、都度1発装填</li>
 * <li>{@link Phase#DONE} — 終了処理後、IdleState / SprintingState へ遷移</li>
 * </ol>
 * LOOP フェーズ中に LEFT_CLICK / RIGHT_CLICK_START でキャンセル可能。
 */
public class ShotgunReloadingState implements WeaponState {

    // ====================== 内部フェーズ ======================

    private enum Phase {
        INTRO, LOOP, OUTRO, DONE
    }

    // ====================== フィールド ======================

    private Phase phase = Phase.INTRO;

    /** フェーズ内の経過Tick */
    private int phaseElapsed = 0;

    /** INTRO フェーズの合計Tick */
    private int introTotalTicks = 0;

    /** LOOP 1周分の合計Tick */
    private int loopTotalTicks = 0;

    /** 装填済みのループ回数（何発装填したか） */
    private int loadsCompleted = 0;

    /** 今回のリロードで装填すべき最大発数 */
    private int maxLoads = 0;

    /** INTRO で使用するアニメーション (reload or tactical_reload) */
    private GunStats.AnimationStats introAnim = null;

    /** LOOP で使用するアニメーション (reload_loop) */
    private GunStats.AnimationStats loopAnim = null;

    /** 弾薬情報 */
    private AmmoDefinition ammoData = null;

    /** インベントリ内の弾薬スタック */
    private List<ItemStack> ammoStacks = null;

    /** リロード可能かどうか */
    private boolean isPossible = false;

    // ====================== WeaponState 実装 ======================

    @Override
    public void onEnter(WeaponContext ctx) {
        GunStats stats = ctx.getStats();
        ItemStack item = ctx.getItem();

        // --- 弾薬を探す ---
        Map<String, List<ItemStack>> ammoPool = ctx.findAmmo(stats.caliber);
        if (ammoPool.isEmpty()) {
            ctx.sendActionBar("§cNo Ammo: " + stats.caliber);
            isPossible = false;
            return;
        }

        // 最も多い弾種を選択
        String bestId = null;
        int maxCount = -1;
        for (Map.Entry<String, List<ItemStack>> entry : ammoPool.entrySet()) {
            int total = entry.getValue().stream().mapToInt(ItemStack::getAmount).sum();
            if (total > maxCount) {
                maxCount = total;
                bestId = entry.getKey();
            }
        }
        ammoData = ItemRegistry.getAmmo(bestId);
        ammoStacks = ammoPool.get(bestId);

        // --- 現在の残弾確認 ---
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        int currentAmmo = pdc.getOrDefault(PDCKeys.AMMO, PDCKeys.INTEGER, 0);
        boolean isEmpty = (currentAmmo <= 0);

        // チューブ内弾数のみカウント（チャンバーはBoltingStateに任せる）
        int currentTotal = currentAmmo;

        // 満タンチェック
        if (currentTotal >= stats.magSize) {
            ctx.sendActionBar("§cMagazine Full");
            isPossible = false;
            return;
        }

        maxLoads = stats.magSize - currentTotal;
        // インベントリにある弾数で上限を絞る
        int inventoryTotal = ammoStacks.stream().mapToInt(ItemStack::getAmount).sum();
        maxLoads = Math.min(maxLoads, inventoryTotal);

        if (maxLoads <= 0) {
            ctx.sendActionBar("§cNo Ammo");
            isPossible = false;
            return;
        }

        isPossible = true;

        // --- アニメーション設定 ---
        // 撃ち切り（弾なし） → tactical_reload、通常（弾あり） → reload
        introAnim = isEmpty ? stats.tacticalReloadAnimation : stats.reloadAnimation;
        // フォールバック
        if (isEmpty && introAnim == null) {
            introAnim = stats.reloadAnimation;
        } else if (!isEmpty && introAnim == null) {
            introAnim = stats.tacticalReloadAnimation;
        }
        loopAnim = stats.reloadLoopAnimation;

        // INTRO の長さ計算
        introTotalTicks = animDurationTicks(introAnim, 20);

        // LOOP 1周の長さ計算
        loopTotalTicks = animDurationTicks(loopAnim, 10);

        // INTRO 開始
        phase = Phase.INTRO;
        phaseElapsed = 0;
        loadsCompleted = 0;

        ctx.setAimProgress(0.0);
        ctx.setSprintProgress(0.0);

        if (introAnim != null) {
            ctx.applyModel(introAnim, 0);
        }
    }

    @Override
    public void onUpdate(WeaponContext ctx) {
        if (!isPossible) {
            transitionOut(ctx);
            return;
        }

        switch (phase) {
            case INTRO -> tickIntro(ctx);
            case LOOP -> tickLoop(ctx);
            case OUTRO -> tickOutro(ctx);
            case DONE -> transitionOut(ctx);
        }
    }

    @Override
    public void onExit(WeaponContext ctx) {
        ctx.sendActionBar("");
        ctx.resetCache();
    }

    @Override
    public WeaponState handleInput(WeaponContext ctx, InputType input) {
        if (!isPossible || phase == Phase.DONE) {
            WeaponState next = new IdleState().handleInput(ctx, input);
            return next != null ? next : new IdleState();
        }

        // INTRO 中は誤操作防止のためほぼ全無視
        if (phase == Phase.INTRO && phaseElapsed < 5) {
            return null;
        }

        switch (input) {
            case LEFT_CLICK, RIGHT_CLICK_START -> {
                // キャンセル → OUTRO へ（即終了ではなく締める動作へ）
                if (phase == Phase.INTRO || phase == Phase.LOOP) {
                    phase = Phase.OUTRO;
                    phaseElapsed = 0;
                }
                return null;
            }
            case RELOAD, SPRINT_START, SPRINT_END -> {
                return null; // リロード中は無視
            }
            default -> {
                return null;
            }
        }
    }

    // ====================== フェーズ処理 ======================

    /** INTRO フェーズの毎Tick処理 */
    private void tickIntro(WeaponContext ctx) {
        phaseElapsed++;

        if (introAnim != null) {
            int frame = calcFrame(phaseElapsed, introAnim);
            ctx.applyModel(introAnim, frame);
        }

        displayReloadBar(ctx);

        if (phaseElapsed >= introTotalTicks) {
            // LOOP フェーズへ移行
            phase = Phase.LOOP;
            phaseElapsed = 0;
            if (loopAnim != null) {
                ctx.applyModel(loopAnim, 0);
            }
        }
    }

    /** LOOP フェーズの毎Tick処理 */
    private void tickLoop(WeaponContext ctx) {
        phaseElapsed++;

        if (loopAnim != null) {
            int frame = calcFrame(phaseElapsed, loopAnim);
            ctx.applyModel(loopAnim, frame);
        }

        displayReloadBar(ctx);

        if (phaseElapsed >= loopTotalTicks) {
            // 1発装填
            boolean loaded = loadOneBullet(ctx);
            loadsCompleted++;
            phaseElapsed = 0;

            if (!loaded || loadsCompleted >= maxLoads) {
                // 装填完了 or 弾が切れた -> OUTRO へ
                phase = Phase.OUTRO;
                phaseElapsed = 0;
            } else {
                // 次の1発へ（ループ継続）
                if (loopAnim != null) {
                    ctx.applyModel(loopAnim, 0);
                }
            }
        }
    }

    /** OUTRO フェーズの毎Tick処理（逆再生） */
    private void tickOutro(WeaponContext ctx) {
        phaseElapsed++;

        // アウトロは常に stats.reloadAnimation を使用する（逆再生）
        GunStats.AnimationStats outroAnim = ctx.getStats().reloadAnimation;
        int totalTicks = animDurationTicks(outroAnim, 10);

        if (outroAnim != null) {
            // 逆再生フレーム計算: (1.0 - 進捗) * frameCount
            double progress = (double) phaseElapsed / totalTicks;
            int frame = (int) ((1.0 - progress) * (outroAnim.frameCount - 1));
            frame = Math.max(0, frame);
            ctx.applyModel(outroAnim, frame);
        }

        if (phaseElapsed >= totalTicks) {
            phase = Phase.DONE;
        }
    }

    // ====================== 装填ロジック ======================

    /**
     * インベントリから弾薬を1発取り出してPDCに加算する。
     *
     * @return 装填できた場合 true
     */
    private boolean loadOneBullet(WeaponContext ctx) {
        // インベントリから1発消費
        boolean consumed = false;
        for (ItemStack stack : ammoStacks) {
            if (stack == null || stack.getType() == Material.AIR || stack.getAmount() <= 0)
                continue;
            stack.setAmount(stack.getAmount() - 1);
            consumed = true;
            break;
        }
        if (!consumed)
            return false;

        // PDC に1発加算
        ItemStack item = ctx.getItem();
        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();

        int current = pdc.getOrDefault(PDCKeys.AMMO, PDCKeys.INTEGER, 0);
        int newAmmo = Math.min(current + 1, ctx.getStats().magSize);
        pdc.set(PDCKeys.AMMO, PDCKeys.INTEGER, newAmmo);
        pdc.set(PDCKeys.CURRENT_AMMO_ID, PDCKeys.STRING, ammoData.id);

        item.setItemMeta(meta);
        ItemFactory.updateLore(item);

        ctx.sendActionBar("§aLoaded §f" + newAmmo + "§7/§f" + ctx.getStats().magSize);
        return true;
    }

    // ====================== ユーティリティ ======================

    /** アニメーション持続Tick を計算する（フォールバックあり） */
    private int animDurationTicks(GunStats.AnimationStats anim, int fallback) {
        if (anim != null && anim.fps > 0 && anim.playbackSpeed > 0) {
            double sec = (double) anim.frameCount / (anim.fps * anim.playbackSpeed);
            return Math.max(1, (int) Math.ceil(sec * 20));
        }
        return fallback;
    }

    /** 経過Tickからフレームインデックスを計算する（最終フレームでクランプ） */
    private int calcFrame(int elapsed, GunStats.AnimationStats anim) {
        int frame = (int) ((elapsed / 20.0) * anim.fps * anim.playbackSpeed);
        return Math.min(frame, anim.frameCount - 1);
    }

    /** リロードバーを表示する */
    private void displayReloadBar(WeaponContext ctx) {
        int bar = 20;
        int filled;
        String label;

        if (phase == Phase.INTRO) {
            filled = (int) ((double) phaseElapsed / introTotalTicks * bar);
            label = "§7Preparing...";
        } else if (phase == Phase.OUTRO) {
            filled = (int) ((1.0 - (double) phaseElapsed / animDurationTicks(ctx.getStats().reloadAnimation, 10))
                    * bar);
            filled = Math.max(0, filled);
            label = "§7Closing...";
        } else {
            // LOOP: 全装填進捗を表示
            int totalProgress = loadsCompleted * loopTotalTicks + phaseElapsed;
            int totalNeeded = maxLoads * loopTotalTicks;
            filled = (int) ((double) totalProgress / totalNeeded * bar);
            label = "§aLoading §f" + loadsCompleted + "§7/§f" + maxLoads;
        }

        StringBuilder sb = new StringBuilder(label + " §7[");
        for (int i = 0; i < bar; i++)
            sb.append(i < filled ? "§a|" : "§8|");
        sb.append("§7]");
        ctx.sendActionBar(sb.toString());
    }

    /** IdleState または SprintingState へ遷移する */
    private void transitionOut(WeaponContext ctx) {
        if (ctx.getStateMachine() == null)
            return;
        if (ctx.getPlayer().isSprinting()) {
            ctx.getStateMachine().transitionTo(new SprintingState());
        } else {
            ctx.getStateMachine().transitionTo(new IdleState());
        }
    }
}
