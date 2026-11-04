package com.lunar_prototype.impossbleEscapeMC.gui;

import java.util.List;
import java.util.Set;

/** チェストホームの項目名・用途・利用条件。ゲーム内の状態を変更しない。 */
final class PdaHomeModel {
    static final int MARKET_LEVEL = 10;
    enum Action {
        RAID, QUESTS, STASH, TRADERS, HIDEOUT_MENU, HIDEOUT, FACILITIES, MARKET, PARTY, SETTINGS, REFRESH, BACK, CLOSE;
        String id() {return name().toLowerCase(java.util.Locale.ROOT);}
        static Action parse(String id) {
            for (Action action : values()) if (action.id().equals(id)) return action;
            return null;
        }
        boolean lobbyOnly() {
            return this == RAID || this == STASH || this == TRADERS || this == HIDEOUT_MENU || this == HIDEOUT || this == FACILITIES || this == MARKET;
        }
    }
    record View(String operator, int level, long experience, long requiredExperience, double balance, int extractions,
                boolean inRaid, boolean inHideout, String party, String invitation, Set<Action> available) {
        View {available = Set.copyOf(available);}
        String location() {return inRaid ? "レイド中" : inHideout ? "隠れ家" : "ロビー";}
    }
    record Entry(Action action, String title, String description, String reason) {
        boolean enabled() {return reason == null;}
        String detail() {return enabled() ? description : reason;}
        String label() {return title + " ｜ " + detail();}
    }

    static String blocked(View view, Action action) {
        if (action == Action.CLOSE || action == Action.REFRESH || action == Action.BACK) return null;
        if (!view.available().contains(action)) return "利用不可";
        if (view.inRaid() && action.lobbyOnly()) return "レイド中";
        if (action == Action.MARKET && view.level() < MARKET_LEVEL) return "Lv.10で解放";
        return null;
    }

    static List<Entry> entries(View view) {
        return List.of(
                entry(view, Action.RAID, "出撃", "レイドを選択"),
                entry(view, Action.QUESTS, "タスク", "目標・進捗"),
                entry(view, Action.STASH, "倉庫", "荷物の保管"),
                entry(view, Action.TRADERS, "商人", "購入・売却"),
                entry(view, Action.HIDEOUT_MENU, "Hideout (隠れ家)", "移動・設備管理"),
                entry(view, Action.MARKET, "市場", "プレイヤー取引"),
                entry(view, Action.PARTY, "パーティー", "招待・管理"),
                entry(view, Action.SETTINGS, "銃の設定", "キー・視点"),
                entry(view, Action.REFRESH, "情報更新", "現在の状態"));
    }

    static List<Entry> hideoutEntries(View view) {
        return List.of(
                entry(view, Action.HIDEOUT, view.inHideout() ? "ロビーへ戻る" : "隠れ家へ移動", view.inHideout() ? "隠れ家を出る" : "拠点へ"),
                entry(view, Action.FACILITIES, "設備管理", "設置・強化"));
    }

    private static Entry entry(View view, Action action, String title, String description) {
        return new Entry(action, title, description, blocked(view, action));
    }
}
