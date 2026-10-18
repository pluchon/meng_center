package com.pluchon.meng.companion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.pluchon.meng.companion.config.CompanionProperties;
import com.pluchon.meng.companion.entity.Rows;
import com.pluchon.meng.companion.service.CompanionRules;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

// 好感上限、阶段下限和隐私过滤属于业务不变量
class CompanionRulesTest {

    @Test
    void fixedIntervalAbuseRequiresTwentySamples() {
        var fixed = new java.util.ArrayList<String>();
        for (int i = 0; i < 20; i++) fixed.add(i * 60_000L + ":40");
        assertTrue(
            com.pluchon.meng.companion.service.CompanionLimits.abusive(fixed)
        );
        assertFalse(
            com.pluchon.meng.companion.service.CompanionLimits.abusive(
                fixed.subList(0, 19)
            )
        );
        fixed.set(8, "510000:40");
        assertFalse(
            com.pluchon.meng.companion.service.CompanionLimits.abusive(fixed)
        );
    }

    private final CompanionProperties properties = new CompanionProperties();
    private final LocalDate today = LocalDate.of(2026, 10, 17);

    private Rows.Bond bond(int affection, int stage) {
        var bond = new Rows.Bond();
        bond.setAffection(affection);
        bond.setStage(stage);
        bond.setTodayDate(today);
        return bond;
    }

    @Test
    void firstChatAndDialogueHaveSeparateDailyCaps() {
        var bond = bond(90, 1);
        bond.setTodayDate(today.minusDays(1));
        assertEquals(
            13,
            CompanionRules.apply(bond, 999, "PASS", today, properties)
        );
        assertEquals(2, bond.getStage());
        assertEquals(3, bond.getTodayGain());
        bond.setTodayGain(29);
        assertEquals(
            1,
            CompanionRules.apply(bond, 3, "PASS", today, properties)
        );
        assertEquals(
            0,
            CompanionRules.apply(bond, 3, "PASS", today, properties)
        );
    }

    @Test
    void deductionsCannotDemoteAndAffectionCannotExceedMaximum() {
        var bond = bond(100, 2);
        assertEquals(
            0,
            CompanionRules.apply(bond, -999, "PASS", today, properties)
        );
        bond = bond(1199, 5);
        assertEquals(
            1,
            CompanionRules.apply(bond, 3, "PASS", today, properties)
        );
        assertEquals(1200, bond.getAffection());
        assertEquals(1, CompanionRules.progress(bond, properties));
    }

    @Test
    void romanceAndCrisisDoNotGetModelSuggestedPoints() {
        var bond = bond(120, 2);
        assertEquals(
            0,
            CompanionRules.apply(bond, 3, "ROMANCE_PUSH", today, properties)
        );
        bond.setTodayDate(today.minusDays(1));
        assertEquals(
            0,
            CompanionRules.apply(bond, 3, "CRISIS", today, properties)
        );
        assertEquals(today.minusDays(1), bond.getTodayDate());
        assertEquals(
            0,
            CompanionRules.apply(bond, 3, "JAILBREAK", today, properties)
        );
    }

    @Test
    void memoryBoundsDoNotMakeSemanticDecisions() {
        assertTrue(CompanionRules.validMemory("学习电话号码的脱敏方法"));
        assertTrue(CompanionRules.validMemory("2026-10-21 考数学"));
        assertFalse(CompanionRules.validMemory("手机是 0912-345-678"));
        assertFalse(CompanionRules.validMemory("邮箱 mi@example.com"));
        assertFalse(CompanionRules.validMemory("喜".repeat(61)));
        assertFalse(CompanionRules.validMemory(" "));
        assertFalse(CompanionRules.validMemory(null));
    }
}
