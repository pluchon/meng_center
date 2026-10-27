package com.pluchon.meng.companion.service;

import com.pluchon.meng.common.core.error.BusinessException;
import com.pluchon.meng.common.core.error.CommonErrorCode;
import com.pluchon.meng.common.core.page.PageResult;
import com.pluchon.meng.companion.controller.CompanionVO;
import com.pluchon.meng.companion.converter.CompanionConverter;
import com.pluchon.meng.companion.entity.Rows;
import com.pluchon.meng.companion.mapper.CompanionMapper;
import com.pluchon.meng.companion.mapper.RuntimeMapper;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

// 会话与记忆独立管理，删除对话不会删除长期记忆。
@Service
public class CompanionService {
    @Autowired private CompanionMapper mapper;
    @Autowired private RuntimeMapper runtime;
    @Autowired private CompanionContext context;
    @Autowired private CompanionRuntimeV2 snapshots;
    @Autowired private StringRedisTemplate redis;
    @Autowired private Clock clock;
    private final JsonMapper json = JsonMapper.builder().findAndAddModules().build();

    public Rows.Session requireSession(long user, long id) {
        var row = mapper.session(id, user);
        if (row == null || !"NEKO".equals(row.getCharacterCode()))
            throw new BusinessException(CommonErrorCode.NOT_FOUND);
        return row;
    }
    public PageResult<CompanionVO.Session> sessions(long user, long page, int size) {
        if (page < 1 || page > 100_000 || size < 1 || size > 50)
            throw new BusinessException(CommonErrorCode.INVALID_ARGUMENT);
        return new PageResult<>(mapper.sessions(user,"NEKO",(page-1)*size,size).stream()
            .map(CompanionConverter::session).toList(), mapper.sessionCount(user,"NEKO"), page, size);
    }
    @Transactional(rollbackFor=Exception.class)
    public CompanionVO.Session createSession(long user, String zone) {
        mapper.ensureBond(user,"NEKO");
        mapper.lockBond(user,"NEKO");
        runtime.zone(user,"NEKO",zone);
        if (mapper.sessionCount(user,"NEKO") >= 50)
            throw new BusinessException(CompanionError.COMPANION_SESSION_LIMIT);
        var session = new Rows.Session();
        session.setUserId(user);
        session.setCharacterCode("NEKO");
        mapper.insertSession(session);
        var opening = new Rows.Message();
        opening.setSessionId(session.getId());
        opening.setRole("CHARACTER");
        opening.setContent(context.card(user,"NEKO").path("opening").asString());
        if (opening.getContent().isBlank() || opening.getContent().length() > 500)
            throw new BusinessException(CompanionError.AI_UNAVAILABLE);
        opening.setBubbles(json.writeValueAsString(List.of(opening.getContent())));
        opening.setCompleted(true);
        mapper.insertMessage(opening);
        mapper.advanceSession(session.getId(),"",1,LocalDateTime.ofInstant(clock.instant(),ZoneOffset.UTC));
        var created = mapper.session(session.getId(),user);
        return new CompanionVO.Session(created.getId(),created.getTitle(),created.getLastMessageTime(),
                                       opening.getContent(),false,false);
    }
    @Transactional(rollbackFor=Exception.class)
    public void deleteSession(long user, long id) {
        var session = requireSession(user,id);
        mapper.lockBond(user,session.getCharacterCode());
        mapper.lockSession(id,user);
        mapper.deleteSession(id,user);
        runtime.failSessionTurns(id);
    }
    public CompanionVO.Messages messages(long user, long id, Long before, int size) {
        requireSession(user,id);
        if (size < 1 || size > 50 || (before != null && before < 1))
            throw new BusinessException(CommonErrorCode.INVALID_ARGUMENT);
        runtime.unread(id,user,false);
        var rows = mapper.messages(id,before,size+1);
        boolean more = rows.size() > size;
        var selected = rows.subList(0,Math.min(size,rows.size()));
        return new CompanionVO.Messages(selected.stream().map(m -> CompanionConverter.message(m,true)).toList(),
                                       more ? selected.get(selected.size()-1).getId() : null);
    }
    @Transactional(rollbackFor=Exception.class)
    public void clearMemories(long user) {
        mapper.ensureBond(user,"NEKO");
        mapper.lockBond(user,"NEKO");
        mapper.clearMemories(user,"NEKO");
        mapper.invalidate(user,"NEKO");
        runtime.clearProfile(user,"NEKO");
        snapshots.invalidate(user,"NEKO");
        redis.delete("meng:companion:memo:"+user);
    }
}
