package com.pluchon.meng.growth.controller.vo;

import java.time.LocalDate;
import java.util.List;

public record GrowthVO(
    int level,
    long exp,
    long levelStartExp,
    Long nextLevelExp,
    boolean checkedIn,
    LocalDate today,
    List<Day> week
) {
    public record Day(LocalDate date, boolean checkedIn) {}
}
