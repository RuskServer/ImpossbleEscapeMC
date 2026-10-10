package com.lunar_prototype.impossbleEscapeMC.item;

import com.lunar_prototype.impossbleEscapeMC.gui.AttachmentLayout;

import java.util.Locale;

public enum AttachmentSlot {
    RECEIVER(0),
    SIGHT(1),
    BARREL(2),
    MAGAZINE(3),
    SPARE_MAGAZINE(4),
    REAR_GRIP(5),
    STOCK(6);

    // 拡張用: UNDER_BARREL, LIGHT, LASER etc.

    private final int id;

    AttachmentSlot(int id) {
        this.id = id;
    }

    public int getId() {
        return id;
    }

    /** アタッチメント画面での位置 (銃の形に合わせた並び。{@link AttachmentLayout}) */
    public int getGuiSlot() {
        return AttachmentLayout.position(key());
    }

    /** データパックのスロット名と同じ形の名前 (sight, rear_grip など) */
    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static AttachmentSlot fromName(String name) {
        try {
            return AttachmentSlot.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
