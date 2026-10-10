package com.lunar_prototype.impossbleEscapeMC.item;

import com.lunar_prototype.impossbleEscapeMC.util.DatapackFunctionUtil;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.component.CustomData;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Toi's Armory データパックのアタッチメント。
 * <ul>
 *   <li>銃ごとのスロットと、そこに付けられるアタッチメント・その効果は storage toisarm:data gun[].attachment</li>
 *   <li>アタッチメントの名前・スロットは storage toisarm:data attachment[]</li>
 *   <li>銃に付いているアタッチメントは、銃アイテムの custom_data.toisarm.attachment に、アタッチメントのアイテムそのものが並ぶ</li>
 * </ul>
 * 付け外しは銃アイテムのリストを書き換えるだけでよい。手に持った銃のデータが変わると、データパックが性能と見た目を計算し直す。
 */
public final class DatapackAttachments {

    /** アタッチメント付与function (マクロ引数: id)。アイテムの構造をプラグイン側に持たない */
    private static final String GIVE_FUNCTION = "toisarm:dialog/get_attachment_with_id/with_trigger_count/with_id/";

    /** 効果 (銃のデータの entry を operation で value だけ変える) */
    public record Modifier(String entry, String operation, double value, String text) {
    }

    /** スロットに付けられるアタッチメントと、その銃での効果 */
    public record Option(String attachmentId, List<Modifier> modifiers) {
    }

    /** 銃のスロットと、そこに付けられるアタッチメント */
    public record SlotDef(String slot, List<Option> options) {
        public Option option(String attachmentId) {
            for (Option option : options) {
                if (option.attachmentId().equals(attachmentId)) return option;
            }
            return null;
        }
    }

    /** アタッチメントの表示名 (翻訳キーと、翻訳が無い時の名前) */
    public record Info(String id, String slot, String translationKey, String fallbackName) {
    }

    private DatapackAttachments() {
    }

    /** 銃のスロット (データパックの並び順)。データパックに銃が無ければ空 */
    public static List<SlotDef> slotsOf(String gunId) {
        List<SlotDef> slots = new ArrayList<>();
        if (gunId == null) return slots;
        CompoundTag gun = storage().getListOrEmpty("gun").compoundStream()
                .filter(g -> gunId.equals(g.getStringOr("id", "")))
                .findFirst().orElse(null);
        if (gun == null) return slots;
        gun.getListOrEmpty("attachment").compoundStream().forEach(slot -> {
            List<Option> options = new ArrayList<>();
            slot.getListOrEmpty("list").compoundStream().forEach(option -> {
                List<Modifier> modifiers = new ArrayList<>();
                option.getListOrEmpty("modifier").compoundStream().forEach(m -> modifiers.add(new Modifier(
                        m.getStringOr("entry", ""), m.getStringOr("operation", ""),
                        m.getDoubleOr("value", Double.NaN), m.getStringOr("value", ""))));
                options.add(new Option(option.getStringOr("id", ""), modifiers));
            });
            slots.add(new SlotDef(slot.getStringOr("slot", ""), options));
        });
        return slots;
    }

    /** アタッチメントの表示名など。データパックに無ければnull */
    public static Info info(String attachmentId) {
        if (attachmentId == null) return null;
        CompoundTag data = storage().getListOrEmpty("attachment").compoundStream()
                .filter(a -> attachmentId.equals(a.getStringOr("id", "")))
                .findFirst().orElse(null);
        if (data == null) return null;
        CompoundTag name = data.getCompoundOrEmpty("display_name");
        String fallback = name.getStringOr("fallback", data.getStringOr("display_name", attachmentId));
        return new Info(attachmentId, data.getStringOr("slot", ""), name.getStringOr("translate", ""), fallback);
    }

    /** データパックのアタッチメントのアイテムなら、そのID (custom_data.toisarm.type が "attachment") */
    public static String attachmentIdOf(ItemStack item) {
        CompoundTag toisarm = toisarmTag(item);
        if (toisarm == null || !"attachment".equals(toisarm.getStringOr("type", ""))) return null;
        String id = toisarm.getStringOr("id", "");
        return id.isEmpty() ? null : id;
    }

    /** データパックのアタッチメントのアイテムなら、付けるスロット */
    public static String slotOf(ItemStack item) {
        CompoundTag toisarm = toisarmTag(item);
        if (toisarm == null || !"attachment".equals(toisarm.getStringOr("type", ""))) return null;
        String slot = toisarm.getStringOr("slot", "");
        return slot.isEmpty() ? null : slot;
    }

    /** 銃に付いているアタッチメント (スロット → アタッチメントのアイテム) */
    public static Map<String, ItemStack> attached(ItemStack gun) {
        Map<String, ItemStack> result = new LinkedHashMap<>();
        CompoundTag toisarm = toisarmTag(gun);
        if (toisarm == null) return result;
        RegistryOps<Tag> ops = ops();
        for (Tag entry : toisarm.getListOrEmpty("attachment")) {
            if (!(entry instanceof CompoundTag stack)) continue;
            String slot = slotOfStoredStack(stack);
            net.minecraft.world.item.ItemStack nms = net.minecraft.world.item.ItemStack.CODEC.parse(ops, stack).result().orElse(null);
            if (slot != null && nms != null && !nms.isEmpty()) result.put(slot, CraftItemStack.asBukkitCopy(nms));
        }
        return result;
    }

    /** 銃に付いているアタッチメントのID (アイテムに戻さず読むだけなので軽い。重さの計算用) */
    public static List<String> attachedIds(ItemStack gun) {
        List<String> ids = new ArrayList<>();
        CompoundTag toisarm = toisarmTag(gun);
        if (toisarm == null) return ids;
        for (Tag entry : toisarm.getListOrEmpty("attachment")) {
            if (!(entry instanceof CompoundTag stack)) continue;
            String id = stack.getCompoundOrEmpty("components").getCompoundOrEmpty("minecraft:custom_data")
                    .getCompoundOrEmpty("toisarm").getStringOr("id", "");
            if (!id.isEmpty()) ids.add(id);
        }
        return ids;
    }

    /**
     * アタッチメントを付けた銃を返す (同じスロットに付いていた物は外れる。外れた物は {@link #attached} で先に取り出しておく)。
     * 銃の状態 (弾数など) には触らない
     */
    public static ItemStack withAttachment(ItemStack gun, ItemStack attachment) {
        String slot = slotOf(attachment);
        if (slot == null) throw new IllegalArgumentException("データパックのアタッチメントではありません");
        net.minecraft.world.item.ItemStack single = CraftItemStack.asNMSCopy(attachment);
        single.setCount(1);
        Tag encoded = net.minecraft.world.item.ItemStack.CODEC.encodeStart(ops(), single).getOrThrow();
        return editAttachmentList(gun, list -> {
            removeSlot(list, slot);
            list.add(encoded);
        });
    }

    /** そのスロットのアタッチメントを外した銃を返す */
    public static ItemStack withoutAttachment(ItemStack gun, String slot) {
        return editAttachmentList(gun, list -> removeSlot(list, slot));
    }

    /** データパックの付与functionでアタッチメントのアイテムを作る。作れなければnull */
    public static ItemStack createItem(World world, String attachmentId) {
        List<ItemStack> items = DatapackFunctionUtil.captureItemsFromFunction(world,
                GIVE_FUNCTION + " {id:\"" + attachmentId.replace("\\", "\\\\").replace("\"", "\\\"") + "\"}");
        return items.isEmpty() ? null : items.get(0);
    }

    /** 効果の説明 (例: "ADS時間 +1", "縦反動 -15%")。見た目だけの変更など、出さない物はnull */
    public static String describe(Modifier modifier) {
        String label = switch (modifier.entry()) {
            case "state.ads_time" -> "ADS時間";
            case "state.ammo_capacity" -> "装弾数";
            case "state.reload_time" -> "リロード時間";
            case "state.empty_reload_time" -> "空リロード時間";
            case "state.sprint_time" -> "ダッシュ移行時間";
            case "state.end_sprint_time" -> "ダッシュ後の構え時間";
            case "state.pitch_recoil_multiplier" -> "縦反動";
            case "state.yaw_recoil_multiplier" -> "横反動";
            case "state.round_per_minute" -> "連射速度";
            case "state.zoom" -> "倍率";
            default -> null;
        };
        if (label == null || Double.isNaN(modifier.value())) return null;
        double v = modifier.value();
        return switch (modifier.operation()) {
            case "add" -> label + " " + signed(v);
            case "add_base_scale" -> label + " " + signed(v * 100) + "%";
            case "scaling" -> label + " ×" + plain(v);
            case "replace" -> label + " → " + plain(v);
            default -> null;
        };
    }

    private static String signed(double v) {
        return (v >= 0 ? "+" : "") + plain(v);
    }

    private static String plain(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.valueOf(Math.round(v * 100) / 100.0);
    }

    private static void removeSlot(ListTag list, String slot) {
        list.removeIf(entry -> entry instanceof CompoundTag stack && slot.equals(slotOfStoredStack(stack)));
    }

    private static String slotOfStoredStack(CompoundTag stack) {
        String slot = stack.getCompoundOrEmpty("components").getCompoundOrEmpty("minecraft:custom_data")
                .getCompoundOrEmpty("toisarm").getStringOr("slot", "");
        return slot.isEmpty() ? null : slot;
    }

    private static ItemStack editAttachmentList(ItemStack gun, java.util.function.Consumer<ListTag> edit) {
        net.minecraft.world.item.ItemStack nms = CraftItemStack.asNMSCopy(gun);
        CompoundTag root = nms.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        CompoundTag toisarm = root.getCompoundOrEmpty("toisarm");
        ListTag list = toisarm.getListOrEmpty("attachment").copy();
        edit.accept(list);
        toisarm.put("attachment", list);
        root.put("toisarm", toisarm);
        nms.set(DataComponents.CUSTOM_DATA, CustomData.of(root));
        return CraftItemStack.asBukkitCopy(nms);
    }

    private static CompoundTag toisarmTag(ItemStack item) {
        if (item == null || item.getType().isAir()) return null;
        CustomData customData = CraftItemStack.unwrap(item).get(DataComponents.CUSTOM_DATA);
        return customData == null ? null : customData.copyTag().getCompoundOrEmpty("toisarm");
    }

    private static CompoundTag storage() {
        return server().getCommandStorage().get(Identifier.fromNamespaceAndPath("toisarm", "data"));
    }

    private static RegistryOps<Tag> ops() {
        return RegistryOps.create(NbtOps.INSTANCE, server().registryAccess());
    }

    private static MinecraftServer server() {
        return ((CraftServer) Bukkit.getServer()).getServer();
    }
}
