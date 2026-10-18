package com.pluchon.meng.companion.service;

import com.pluchon.meng.companion.config.CompanionProperties;
import com.pluchon.meng.companion.entity.Rows;
import java.time.LocalDate;
import java.util.regex.Pattern;

// 可独立验证的好感、记忆和固定台词规则
public final class CompanionRules {

    private CompanionRules() {}

    // 格式化的联系方式和证件号（手机号、邮箱、15～18 位证件号），和 ai 的兜底规则一致；语义上的隐私由 AI 审核
    private static final Pattern CONTACT = Pattern.compile(
        "(?<!\\d)(?:\\+?\\d{1,3}[\\s-]?)?\\d{3,4}[\\s-]?\\d{3,4}[\\s-]?\\d{3,4}(?!\\d)" +
            "|[\\w.+-]+@[\\w-]+\\.[\\w.-]+|(?<!\\d)\\d{15,17}[\\dXx](?!\\d)"
    );

    public static boolean validMemory(String text) {
        return (
            text != null &&
            !text.isBlank() &&
            text.codePointCount(0, text.length()) <= 60 &&
            !CONTACT.matcher(text).find()
        );
    }

    public static int apply(
        Rows.Bond bond,
        int suggested,
        String label,
        LocalDate date,
        CompanionProperties p
    ) {
        if (
            "CRISIS".equals(label) ||
            "SEXUAL".equals(label) ||
            "JAILBREAK".equals(label)
        ) return 0;
        boolean first = !date.equals(bond.getTodayDate());
        int old = bond.getAffection();
        int gain = first ? 0 : bond.getTodayGain();
        int delta = "ROMANCE_PUSH".equals(label)
            ? 0
            : Math.max(-2, Math.min(3, suggested));
        if (delta > 0) delta = Math.min(
            delta,
            Math.max(0, p.getDailyGain() - gain)
        );
        int floor = p.getThresholds().get(bond.getStage() - 1);
        int result = Math.min(
            p.getAffectionMax(),
            Math.max(floor, old + delta + (first ? p.getFirstChatGain() : 0))
        );
        bond.setAffection(result);
        bond.setTodayDate(date);
        bond.setTodayGain(gain + Math.max(0, delta));
        int stage = bond.getStage();
        while (
                stage < p.getThresholds().size() &&
                result >= p.getThresholds().get(stage)
            )
            stage++;
        bond.setStage(stage);
        return result - old;
    }

    public static double progress(Rows.Bond bond, CompanionProperties p) {
        if (bond == null) return 0;
        int start = p.getThresholds().get(bond.getStage() - 1);
        int end =
            bond.getStage() == 5
                ? p.getAffectionMax()
                : p.getThresholds().get(bond.getStage());
        return Math.max(
            0,
            Math.min(
                1,
                (double) (bond.getAffection() - start) /
                    Math.max(1, end - start)
            )
        );
    }
}
