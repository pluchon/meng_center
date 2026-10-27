package com.pluchon.meng.companion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.pluchon.meng.companion.config.CompanionProperties;
import com.pluchon.meng.companion.entity.Rows;
import com.pluchon.meng.companion.service.CompanionRules;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

// 确定性的长度、联系方式与访问频率校验。
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
