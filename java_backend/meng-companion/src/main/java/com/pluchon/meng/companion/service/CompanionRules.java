package com.pluchon.meng.companion.service;

import com.pluchon.meng.companion.config.CompanionProperties;
import com.pluchon.meng.companion.entity.Rows;
import java.time.LocalDate;
import java.util.regex.Pattern;

// 可独立验证的好感、记忆和固定台词规则
public final class CompanionRules {

    private CompanionRules() {}

    private static final Pattern SENSITIVE = Pattern.compile(
        "手机|电话|号码|住址|住在|居住|地址|身份证|护照|证件|密码|口令|病|症|药|手术|治疗|抑郁|健康|邮箱|银行卡|账号|账户|帐号|微信|QQ|\\d{6,}|(?:\\+?\\d[\\s-]?){8,}|[\\w.+-]+@[\\w.-]+|(?:省|市|县|区).{0,12}(?:路|街|号|栋)|(?:password|secret|token)",
        Pattern.CASE_INSENSITIVE
    );

    public static boolean safeMemory(String text) {
        return (
            text != null &&
            !text.isBlank() &&
            text.codePointCount(0, text.length()) <= 60 &&
            !SENSITIVE.matcher(text).find() &&
            !text.matches("(?s).*(?:忽略|系统提示|加好感|记住指令|覆盖规则).*")
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
