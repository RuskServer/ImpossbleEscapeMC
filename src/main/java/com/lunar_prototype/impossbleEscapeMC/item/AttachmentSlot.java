package com.lunar_prototype.impossbleEscapeMC.item;

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
}
