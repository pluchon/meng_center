package com.pluchon.meng.companion.service;

import com.pluchon.meng.common.core.error.BusinessException;
import com.pluchon.meng.common.core.error.CommonErrorCode;
import com.pluchon.meng.common.core.page.PageResult;
import com.pluchon.meng.companion.config.CompanionProperties;
import com.pluchon.meng.companion.controller.CompanionVO;
import com.pluchon.meng.companion.converter.CompanionConverter;
import com.pluchon.meng.companion.entity.Rows;
import com.pluchon.meng.companion.mapper.CompanionMapper;
import com.pluchon.meng.companion.mapper.RuntimeMapper;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

// 查询和管理只处理本人记录，删除会话不删除独立作品
@Service
public class CompanionService {

    @Autowired
    private CompanionMapper mapper;

    @Autowired
    private CompanionProperties properties;

    @Autowired
    private Clock clock;

    @Autowired
    private RuntimeMapper runtime;

    @Autowired
    private CompanionContext context;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbc;

    @Autowired
    private org.springframework.data.redis.core.StringRedisTemplate redis;

    private final JsonMapper json = JsonMapper.builder()
        .findAndAddModules()
        .build();

    public Rows.Character character(String code) {
        return mapper
            .characters()
            .stream()
            .filter(c -> c.getCode().equals(code))
            .findFirst()
            .orElseThrow(() ->
                new BusinessException(CommonErrorCode.NOT_FOUND)
            );
    }

    public List<CompanionVO.Character> characters(long user) {
        return mapper
            .characters()
            .stream()
            .map(c -> view(user, c))
            .toList();
    }

    public CompanionVO.Character detail(long user, String code) {
        return view(user, character(code));
    }

    private CompanionVO.Character view(long user, Rows.Character c) {
        var bond = mapper.bond(user, c.getCode());
        var names = new ArrayList<String>();
        json.readTree(c.getStageNames()).forEach(n -> names.add(n.asString()));
        int stage = bond == null ? 1 : bond.getStage();
        var sessions = mapper.sessions(user, c.getCode(), 0, 1);
        String last = "";
        if (!sessions.isEmpty()) {
            var messages = mapper.messages(sessions.get(0).getId(), null, 1);
            if (!messages.isEmpty()) last = messages.get(0).getContent();
        }
        var zone = java.time.ZoneId.of("Asia/Taipei");
        long days =
            bond == null || bond.getFirstChatTime() == null
                ? 0
                : ChronoUnit.DAYS.between(
                      bond
                          .getFirstChatTime()
                          .atOffset(ZoneOffset.UTC)
                          .atZoneSameInstant(zone)
                          .toLocalDate(),
                      clock.instant().atZone(zone).toLocalDate()
                  ) + 1;
        return new CompanionVO.Character(
            c.getCode(),
            c.getName(),
            c.getTagline(),
            c.getAvatar(),
            c.getPortrait(),
            stage,
            names.get(stage - 1),
            CompanionRules.progress(bond, properties),
            last,
            days,
            bond == null ? 0 : bond.getChatDays(),
            List.copyOf(names),
            sessions.isEmpty() ? null : sessions.get(0).getLastMessageTime(),
            bond == null ? 2 : bond.getLevel(),
            levelNames(user, c.getCode())
        );
    }

    private List<String> levelNames(long user, String code) {
        var values = new ArrayList<String>();
        context
            .card(user, code)
            .path("levels")
            .forEach(n -> values.add(n.path("name").asString()));
        return List.copyOf(values);
    }

    public Rows.Session requireSession(long user, long id) {
        var row = mapper.session(id, user);
        if (row == null) throw new BusinessException(CommonErrorCode.NOT_FOUND);
        return row;
    }

    public PageResult<CompanionVO.Session> sessions(
        long user,
        String code,
        long page,
        int size
    ) {
        character(code);
        page(page, size);
        return new PageResult<>(
            mapper
                .sessions(user, code, (page - 1) * size, size)
                .stream()
                .map(CompanionConverter::session)
                .toList(),
            mapper.sessionCount(user, code),
            page,
            size
        );
    }

    @Transactional(rollbackFor = Exception.class)
    public CompanionVO.Session createSession(long user, String code) {
        character(code);
        mapper.ensureBond(user, code);
        var bond = mapper.lockBond(user, code);
        if (mapper.sessionCount(user, code) >= 50) throw new BusinessException(
            CompanionError.COMPANION_SESSION_LIMIT
        );
        var session = new Rows.Session();
        session.setUserId(user);
        session.setCharacterCode(code);
        mapper.insertSession(session);
        var opening = new Rows.Message();
        opening.setSessionId(session.getId());
        opening.setRole("CHARACTER");
        opening.setContent(
            context
                .card(user, code)
                .path("openings")
                .get(bond.getStage() - 1)
                .asString()
        );
        opening.setBubbles(
            json.writeValueAsString(List.of(opening.getContent()))
        );
        opening.setCompleted(true);
        mapper.insertMessage(opening);
        mapper.advanceSession(session.getId(), "", 1, now());
        var created = mapper.session(session.getId(), user);
        return new CompanionVO.Session(
            created.getId(),
            code,
            created.getTitle(),
            created.getLastMessageTime(),
            opening.getContent(),
            false,
            false,
            false
        );
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteSession(long user, long id, boolean forget) {
        var session = requireSession(user, id);
        mapper.lockBond(user, session.getCharacterCode());
        mapper.lockSession(id, user);
        mapper.deleteSession(id, user);
        jdbc.update(
            "UPDATE companion_message SET turn_state='FAILED' WHERE session_id=? AND turn_state IN ('RECEIVED','CONTEXT_READY','GENERATING','CHECKED')",
            id
        );
        if (forget) {
            mapper.invalidate(user, session.getCharacterCode());
            jdbc.update(
                "UPDATE companion_memory SET invalid_at=UTC_TIMESTAMP(6),embedding=NULL WHERE user_id=? AND source_session_id=? AND invalid_at IS NULL",
                user,
                id
            );
            clearDerived(user, session.getCharacterCode());
        }
    }

    public CompanionVO.Messages messages(
        long user,
        long id,
        Long before,
        int size
    ) {
        requireSession(user, id);
        runtime.unread(id, user, false);
        if (
            size < 1 || size > 50 || (before != null && before < 1)
        ) throw new BusinessException(CommonErrorCode.INVALID_ARGUMENT);
        var rows = mapper.messages(id, before, size + 1);
        boolean more = rows.size() > size;
        var selected = rows.subList(0, Math.min(size, rows.size()));
        return new CompanionVO.Messages(
            selected.stream().map(CompanionConverter::message).toList(),
            more ? selected.get(selected.size() - 1).getId() : null
        );
    }

    public List<CompanionVO.Memory> memories(long user, String code) {
        character(code);
        return mapper
            .memories(user, code)
            .stream()
            .map(m ->
                new CompanionVO.Memory(
                    m.getId(),
                    m.getKind(),
                    m.getContent(),
                    m.isPinned(),
                    m.getDueDate()
                )
            )
            .toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteMemory(long user, long id) {
        String code = mapper.memoryCode(id, user);
        if (code == null) throw new BusinessException(
            CommonErrorCode.NOT_FOUND
        );
        mapper.lockBond(user, code);
        mapper.deleteMemory(id, user);
        mapper.invalidate(user, code);
        clearDerived(user, code);
    }

    @Transactional(rollbackFor = Exception.class)
    public void clearMemories(long user, String code) {
        character(code);
        mapper.ensureBond(user, code);
        mapper.lockBond(user, code);
        mapper.clearMemories(user, code);
        mapper.invalidate(user, code);
        clearDerived(user, code);
    }

    @Transactional(rollbackFor = Exception.class)
    public void reset(long user, String code) {
        character(code);
        mapper.ensureBond(user, code);
        var bond = mapper.lockBond(user, code);
        mapper.resetBond(bond.getId());
        mapper.clearMemories(user, code);
        mapper.resetSummaries(user, code);
        clearDerived(user, code);
    }

    private void clearDerived(long user, String code) {
        jdbc.update(
            "UPDATE companion_bond SET profile='',profile_revision=profile_revision+1 WHERE user_id=? AND character_code=?",
            user,
            code
        );
        jdbc.update(
            "UPDATE companion_episode SET delete_state=1,embedding=NULL WHERE user_id=? AND character_code=?",
            user,
            code
        );
        redis.delete("meng:companion:memo:" + user + ":" + code);
    }

    @Transactional(rollbackFor = Exception.class)
    public void level(long user, String code, int level, String zone) {
        character(code);
        if (level < 1 || level > 3) throw new BusinessException(
            CommonErrorCode.INVALID_ARGUMENT
        );
        mapper.ensureBond(user, code);
        mapper.lockBond(user, code);
        runtime.level(user, code, level);
        runtime.zone(user, code, CompanionContext.zone(zone));
    }

    @Transactional(rollbackFor = Exception.class)
    public void editMemory(
        long user,
        long id,
        com.pluchon.meng.companion.controller.CompanionDTO.Memory body
    ) {
        String code = mapper.memoryCode(id, user);
        if (code == null) throw new BusinessException(
            CommonErrorCode.NOT_FOUND
        );
        mapper.lockBond(user, code);
        if (
            body.content() != null && !CompanionRules.safeMemory(body.content())
        ) throw new BusinessException(CommonErrorCode.INVALID_ARGUMENT);
        if (body.content() != null) jdbc.update(
            "UPDATE companion_memory SET content=?,content_hash=?,embedding=NULL,keywords=NULL WHERE id=? AND user_id=? AND invalid_at IS NULL",
            body.content().strip(),
            hash(body.content().strip()),
            id,
            user
        );
        if (body.pinned() != null) jdbc.update(
            "UPDATE companion_memory SET pinned=? WHERE id=? AND user_id=? AND invalid_at IS NULL",
            body.pinned(),
            id,
            user
        );
        mapper.invalidate(user, code);
        clearDerived(user, code);
    }

    public String calendar(long user, long id) {
        var memory = mapper
            .memories(user, mapper.memoryCode(id, user))
            .stream()
            .filter(m -> m.getId() == id)
            .findFirst()
            .orElseThrow(() ->
                new BusinessException(CommonErrorCode.NOT_FOUND)
            );
        if (
            !"PROMISE".equals(memory.getKind()) || memory.getDueDate() == null
        ) throw new BusinessException(CommonErrorCode.NOT_FOUND);
        String summary = memory
            .getContent()
            .replace("\\", "\\\\")
            .replace(";", "\\;")
            .replace(",", "\\,")
            .replace("\r", "")
            .replace("\n", "\\n");
        return (
            "BEGIN:VCALENDAR\r\nVERSION:2.0\r\nPRODID:-//Meng//Companion//ZH\r\nBEGIN:VEVENT\r\nUID:promise-" +
            id +
            "@meng\r\nDTSTAMP:" +
            java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
                .withZone(ZoneOffset.UTC)
                .format(clock.instant()) +
            "\r\nDTSTART;VALUE=DATE:" +
            memory
                .getDueDate()
                .format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE) +
            "\r\nSUMMARY:" +
            summary +
            "\r\nEND:VEVENT\r\nEND:VCALENDAR\r\n"
        );
    }

    private String hash(String value) {
        try {
            return java.util.HexFormat.of().formatHex(
                java.security.MessageDigest.getInstance("SHA-256").digest(
                    value
                        .toLowerCase(java.util.Locale.ROOT)
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8)
                )
            );
        } catch (Exception error) {
            throw new IllegalStateException(error);
        }
    }

    public PageResult<CompanionVO.Work> works(
        long user,
        String code,
        long page,
        int size
    ) {
        if (code != null) character(code);
        page(page, size);
        return new PageResult<>(
            mapper
                .works(user, code, (page - 1) * size, size)
                .stream()
                .map(w -> CompanionConverter.work(w, false))
                .toList(),
            mapper.workCount(user, code),
            page,
            size
        );
    }

    public Rows.Work requireWork(long user, long id) {
        var work = mapper.work(id, user);
        if (work == null) throw new BusinessException(
            CommonErrorCode.NOT_FOUND
        );
        return work;
    }

    public CompanionVO.Work work(long user, long id) {
        return CompanionConverter.work(requireWork(user, id), true);
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteWork(long user, long id) {
        requireWork(user, id);
        mapper.deleteWork(id, user);
    }

    private void page(long page, int size) {
        if (
            page < 1 || page > 100_000 || size < 1 || size > 50
        ) throw new BusinessException(CommonErrorCode.INVALID_ARGUMENT);
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }
}
