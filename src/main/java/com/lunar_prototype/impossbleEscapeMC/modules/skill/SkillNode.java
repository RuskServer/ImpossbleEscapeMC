package com.lunar_prototype.impossbleEscapeMC.modules.skill;

import com.lunar_prototype.impossbleEscapeMC.modules.hideout.HideoutFacility;

import java.util.List;
import java.util.Map;

/**
 * スキルツリーのノード 1 つ (ランクは無く、1 ノード = 1 効果)。
 *
 * @param id        保存に使う id (「shooting.reload」。変えない)
 * @param number    設計書 (docs/SKILL_TREE.md) の表の番号
 * @param tier      ティア 1〜3 (隠れ家のスキルツリー設備のレベルで開く)
 * @param effect    画面に出す効果の字
 * @param requires  前提のノードの id
 * @param anyOf     前提はどれか 1 つでよい
 * @param money     費用 (₽)
 * @param materials 費用の素材 (スタッシュと持ち物から取る)
 * @param row       ツリーの段 1〜5 (ティア I が 1・2 段、II が 3・4 段、III が 5 段)
 * @param col2      ツリーの列の 2 倍 (0〜8。奇数は半列ずらした段)
 * @param icon      絵の番号 (フォント iem:skill。IEM_shader/tools/skill_icons.py の並び)
 * @param stats     習得すると足される値
 */
public record SkillNode(String id, SkillCategory category, int number, int tier, String name, String effect,
                        List<String> requires, boolean anyOf, long money, List<HideoutFacility.ItemCost> materials,
                        int row, int col2, int icon, Map<SkillStat, Double> stats) {
}
