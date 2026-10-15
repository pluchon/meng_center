package com.pluchon.meng.game.service;

import com.pluchon.meng.common.core.error.ErrorCode;

// 游戏里的拒绝原因
public enum GameError implements ErrorCode {
    TOO_FAR("GAME_TOO_FAR", "离得太远了，再走近一点", 400),
    OUT_OF_ISLAND("GAME_OUT_OF_ISLAND", "位置不在岛上", 400),
    NOT_ENOUGH_COINS("GAME_NOT_ENOUGH_COINS", "金币不够", 409),
    ITEM_OWNED("GAME_ITEM_OWNED", "已经拥有这件了", 409),
    ITEM_NOT_OWNED("GAME_ITEM_NOT_OWNED", "还没有这件", 409),
    STARS_MISSING("GAME_STARS_MISSING", "星之碎片还没集齐", 409);

    private final String code;
    private final String message;
    private final int status;

    GameError(String code, String message, int status) {
        this.code = code;
        this.message = message;
        this.status = status;
    }

    public String code() {
        return code;
    }

    public String message() {
        return message;
    }

    public int httpStatus() {
        return status;
    }
}
