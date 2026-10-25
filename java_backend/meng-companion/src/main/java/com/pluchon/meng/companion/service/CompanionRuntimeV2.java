package com.pluchon.meng.companion.service;

import com.pluchon.meng.companion.entity.Rows;
import com.pluchon.meng.companion.mapper.CompanionMapper;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import com.pluchon.meng.companion.mapper.RuntimeV2Mapper;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

// 快照保存原文，记忆失效和用户编辑后刷新，热聊期间保持前缀不变。
@Service
public class CompanionRuntimeV2 {
    @Autowired private RuntimeV2Mapper state;
    @Autowired private Clock clock;
    @Autowired private com.pluchon.meng.companion.config.CompanionProperties properties;
    @Autowired private CompanionMapper mapper;
    private final JsonMapper json = JsonMapper.builder().findAndAddModules().build();

    public JsonNode snapshot(Rows.Session session, Rows.Bond bond, boolean force) {
        var all = mapper.memories(session.getUserId(), session.getCharacterCode());
        var current = new LinkedHashMap<Long, Rows.Memory>();
        all.forEach(m -> current.put(m.getId(), m));
        JsonNode previous = session.getContextSnapshot() == null ? null : json.readTree(session.getContextSnapshot());
        boolean refresh = force || previous == null;
        if (previous != null) {
            for (var old : previous.path("memories")) {
                var now = current.get(old.path("id").asLong());
                if (now == null || !now.getContent().equals(old.path("content").asString()) ||
                    now.isPinned() != old.path("pinned").asBoolean()) refresh = true;
            }

        }
        if (!refresh) return previous;
        var memories = new ArrayList<Map<String,Object>>();
        int bits = 0;
        for (var m : all) {
            if (!m.isPinned() && !"SELF".equals(m.getKind()) && !"BIT".equals(m.getKind())) continue;
            if ("BIT".equals(m.getKind()) && bits++ >= 5) continue;
            var row = new LinkedHashMap<String,Object>();
            row.put("id", m.getId()); row.put("kind", m.getKind()); row.put("content", m.getContent());
            row.put("pinned", m.isPinned()); row.put("validFrom", m.getValidFrom()); memories.add(row);
        }
        var value = json.valueToTree(Map.of("profile", Objects.requireNonNullElse(bond.getProfile(), ""), "memories", memories));
        state.snapshot(
            value.toString(), LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC), session.getId(), session.getUserId());
        session.setContextSnapshot(value.toString());
        session.setSnapshotTime(LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
        return value;
    }

    public void invalidate(long user,String code) { state.invalidateSnapshots(user,code); }

    public void metadata(Rows.Message message, JsonNode result, boolean retract) {
        var sources = sources(result.path("sources"));
        message.setSources(json.writeValueAsString(sources));
        message.setRetracted(retract);
        message.setTyping(result.path("typing").asString("NORMAL"));
        state.metadata( message.getSources(), retract, message.getId());
    }

    public List<Map<String,String>> sources(JsonNode nodes) {
        if (nodes.isMissingNode() || nodes.isNull()) return List.of();
        if (!nodes.isArray() || nodes.size() > 20) throw new IllegalArgumentException("来源数量不合法");
        var result = new ArrayList<Map<String,String>>();
        for (var source : nodes) {
            String url = source.path("url").asString();
            if (!validUrl(url)) throw new IllegalArgumentException("来源链接不合法");
            String title = source.path("title").asString(), site = source.path("site").asString();
            if (title.length()>200 || site.length()>255) throw new IllegalArgumentException("来源字段过长");
            result.add(Map.of("title",title,"url",url,"site",site));
        }
        return result;
    }

    public static boolean validUrl(String value) {
        try { var uri = java.net.URI.create(value);
            return value.length() <= 2000 && Set.of("http","https").contains(uri.getScheme()) &&
                uri.getHost()!=null && uri.getUserInfo()==null;
        } catch (RuntimeException error) { return false; }
    }

    public void threads(long session, long user, JsonNode threads) {
        if (!threads.isArray() || threads.size()>5) throw new IllegalArgumentException("话题线不合法");
        for (var row:threads) if (!row.isString() || row.asString().length()>300)
            throw new IllegalArgumentException("话题线过长");
        state.threads( threads.toString(),session,user);
    }

    public void reaction(long message, long session, String emotion) {
        if (!emotion.matches("[^\\s\\[\\]]{1,12}")) throw new IllegalArgumentException("表情记号不合法");
        state.reaction(emotion,message,session);
    }

    public String status(String code, LocalDate date) {
        var rows = state.status(code,date);
        return rows.isEmpty()?"":rows.get(0);
    }

    public String calendar(Rows.Bond bond, LocalDate date) {
        if(date.equals(bond.getTodayDate())) return "";
        String holiday = properties.getHolidays().getOrDefault(date.toString(),
            properties.getHolidays().getOrDefault(date.toString().substring(5), ""));
        if (Set.of("情人节","七夕").contains(holiday)) holiday="";
        if (bond.getFirstChatTime()!=null) {
            var first = bond.getFirstChatTime().atOffset(ZoneOffset.UTC)
                .atZoneSameInstant(ZoneId.of(bond.getTimeZone())).toLocalDate();
            long days = java.time.temporal.ChronoUnit.DAYS.between(first,date)+1;
            if (days==100 || (date.getYear()>first.getYear() && MonthDay.from(date).equals(MonthDay.from(first)))) holiday += "；你们认识第"+days+"天";
        }
        for(var memory:mapper.memories(bond.getUserId(),bond.getCharacterCode())) {
            if ("DATE".equals(memory.getKind()) && memory.getDueDate()!=null &&
                MonthDay.from(memory.getDueDate()).equals(MonthDay.from(date))) holiday += "；"+memory.getContent();
        }
        return holiday;
    }
    public static int tokens(String value) {
        if(value==null) return 0;
        double size=0;
        for(int c:value.codePoints().toArray()) size+=c>127?1:0.25;
        return (int)Math.ceil(size);
    }
}
