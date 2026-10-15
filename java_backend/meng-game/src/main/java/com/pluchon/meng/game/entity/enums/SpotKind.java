package com.pluchon.meng.game.entity.enums;

// 点位种类：金币每天刷新，宝箱和星之碎片只能拿一次
public enum SpotKind {
    COIN(true),
    CHEST(false),
    STAR(false);

    // 是否每天刷新
    private final boolean daily;

    SpotKind(boolean daily) {
        this.daily = daily;
    }

    public boolean daily() {
        return daily;
    }
}
