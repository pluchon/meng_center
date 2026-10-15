package com.pluchon.meng.game.controller.vo;

// 购买或点亮后的金币变化：amount 是这次的增减，coins 是最新余额
public record CoinsVO(int amount, long coins) {}
