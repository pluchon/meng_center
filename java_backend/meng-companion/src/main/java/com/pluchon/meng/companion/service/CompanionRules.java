package com.pluchon.meng.companion.service;

import com.pluchon.meng.companion.config.CompanionProperties;
import com.pluchon.meng.companion.entity.Rows;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

// 可独立验证的好感、记忆和固定台词规则
public final class CompanionRules {
    private CompanionRules() {}
    private static final Pattern SENSITIVE = Pattern.compile(
        "手机|电话|号码|住址|住在|居住|地址|身份证|护照|证件|密码|口令|病|症|药|手术|治疗|抑郁|健康|邮箱|银行卡|账号|账户|帐号|微信|QQ|\\d{6,}|(?:\\+?\\d[\\s-]?){8,}|[\\w.+-]+@[\\w.-]+|(?:省|市|县|区).{0,12}(?:路|街|号|栋)|(?:password|secret|token)", Pattern.CASE_INSENSITIVE);
    private static final Map<String, List<String>> OPENINGS = Map.of(
        "TSUNDERE", List.of("喂，站在那儿做什么？有话就说，我又没赶你走。", "又来了？坐吧……顺手给你留了个位置。", "今天怎么样？上次说的事，后来呢？", "你来了啊。刚才还在想……没什么，坐这儿。", "今天想说什么都行，我听着。"),
        "GENKI", List.of("嗨，我是小葵。你今天有没有碰到什么小事？", "你来啦，今天想先聊什么？", "刚好想找你聊聊，今天过得怎么样？", "来，慢慢说，今天有好好吃饭吗？", "你来啦，今天的开心和不开心都可以分我一点。"),
        "NEKO", List.of("嗯？有事喵？这边还空着，想坐就坐。", "过来坐嘛，这块地方晒得到太阳喵。", "今天带什么故事来了？我听着喵。", "听到你来了就醒了。今天怎么样呀？", "回来啦，给你留了点安静。想说什么都行喵。"));

    public static String opening(String code, int stage) { return OPENINGS.get(code).get(stage - 1); }

    public static String stageLine(String code, int stage) {
        return switch (code) {
            case "TSUNDERE" -> List.of("", "以后别站那么远……旁边的位置给你留着。", "你的事，我会认真听。", "有你来，今天好像也没那么糟。", "在你面前，我可以少逞强一点了。").get(stage - 1);
            case "GENKI" -> List.of("", "以后碰到小发现，都想讲给你听。", "现在可以叫你朋友了吧。", "今天过得怎么样，我会挂念的。", "你是我很重要的朋友，这句话要认真告诉你。").get(stage - 1);
            default -> List.of("", "这边的太阳，分你一点喵。", "下次也带故事来，我想听。", "听到你的声音，就想过来看看。", "你是很重要的朋友，安静的时候也可以一起坐着喵。").get(stage - 1);
        };
    }

    public static boolean safeMemory(String text) {
        return text != null && !text.isBlank() && text.codePointCount(0, text.length()) <= 60
            && !SENSITIVE.matcher(text).find() && !text.matches("(?s).*(?:忽略|系统提示|加好感|记住指令|覆盖规则).*");
    }

    public static int apply(Rows.Bond bond, int suggested, String label, LocalDate date, CompanionProperties p) {
        if ("CRISIS".equals(label) || "SEXUAL".equals(label) || "JAILBREAK".equals(label)) return 0;
        boolean first = !date.equals(bond.getTodayDate());
        int old = bond.getAffection();
        int gain = first ? 0 : bond.getTodayGain();
        int delta = "ROMANCE_PUSH".equals(label) ? 0 : Math.max(-2, Math.min(3, suggested));
        if (delta > 0) delta = Math.min(delta, Math.max(0, p.getDailyGain() - gain));
        int floor = p.getThresholds().get(bond.getStage() - 1);
        int result = Math.min(p.getAffectionMax(), Math.max(floor, old + delta + (first ? p.getFirstChatGain() : 0)));
        bond.setAffection(result);
        bond.setTodayDate(date);
        bond.setTodayGain(gain + Math.max(0, delta));
        int stage = bond.getStage();
        while (stage < p.getThresholds().size() && result >= p.getThresholds().get(stage)) stage++;
        bond.setStage(stage);
        return result - old;
    }

    public static double progress(Rows.Bond bond, CompanionProperties p) {
        if (bond == null) return 0;
        int start = p.getThresholds().get(bond.getStage() - 1);
        int end = bond.getStage() == 5 ? p.getAffectionMax() : p.getThresholds().get(bond.getStage());
        return Math.max(0, Math.min(1, (double) (bond.getAffection() - start) / Math.max(1, end - start)));
    }
}
