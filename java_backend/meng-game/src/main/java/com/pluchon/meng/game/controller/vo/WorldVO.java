package com.pluchon.meng.game.controller.vo;

import com.pluchon.meng.game.entity.enums.ItemKind;
import com.pluchon.meng.game.entity.enums.SpotKind;

import java.util.List;

// 世界清单：点位（含我这个周期是否已拿）、神殿位置和商品（含我是否已拥有）
public record WorldVO(Point shrine, List<Spot> spots, List<Item> items) {

    // 一个水平位置
    public record Point(double x, double z) {}

    // 一个点位，amount 是拿到时得多少金币；y 是高处点位的地面高度，空表示在地面上
    public record Spot(String id, SpotKind kind, double x, double z, Double y, int amount, boolean taken) {}

    // 一件商品
    public record Item(String id, ItemKind kind, int price, boolean owned) {}
}
