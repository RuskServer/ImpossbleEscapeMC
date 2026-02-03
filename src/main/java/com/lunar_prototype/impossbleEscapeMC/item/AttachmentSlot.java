package com.lunar_prototype.impossbleEscapeMC.item;

public enum AttachmentSlot {
    RECEIVER(0, 10),
    SIGHT(1, 11),
    BARREL(2, 12),
    MAGAZINE(3, 14),
    SPARE_MAGAZINE(4, 15),
    REAR_GRIP(5, 16),
    STOCK(6, 19);

    // 拡張用: UNDER_BARREL, LIGHT, LASER etc.

    private final int id;
    private final int guiSlot;

    AttachmentSlot(int id, int guiSlot) {
        this.id = id;
        this.guiSlot = guiSlot;
    }

    public int getId() {
        return id;
    }

    public int getGuiSlot() {
        return guiSlot;
    }

    public static AttachmentSlot fromName(String name) {
        try {
            return AttachmentSlot.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
