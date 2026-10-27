package com.pluchon.meng.companion;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.pluchon.meng.companion.client.*;
import com.pluchon.meng.companion.config.CompanionProperties;
import com.pluchon.meng.companion.controller.CompanionDTO;
import com.pluchon.meng.companion.entity.Rows;
import com.pluchon.meng.companion.mapper.*;
import com.pluchon.meng.companion.service.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.*;
import org.mybatis.spring.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

// 真实 MyBatis/H2 事务配合录制的模型响应，覆盖连接断开、幂等、合并与回滚。
public class CompanionRuntimeTest {

    public static java.sql.Timestamp utcTimestamp(int precision) {
        return java.sql.Timestamp.from(Instant.parse("2026-10-17T04:00:00Z"));
    }

    private final JsonMapper json = JsonMapper.builder()
        .findAndAddModules()
        .build();
    private JdbcTemplate jdbc;
    private ReplyService replies;
    private CompanionService service;
    private AiClient ai;
    private CompanionLimits limits;
    private CompanionOutbox outbox;
    private ThreadPoolTaskExecutor executor;
    private final CountDownLatch entered = new CountDownLatch(1),
        release = new CountDownLatch(1);
    private final List<Map<String, Object>> requests =
        new CopyOnWriteArrayList<>();
    private final Set<Long> locks = ConcurrentHashMap.newKeySet();

    @BeforeEach
    void setup() throws Exception {
        var source = new JdbcDataSource();
        source.setURL(
            "jdbc:h2:mem:companion" +
                UUID.randomUUID() +
                ";MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=LEVEL,DATE"
        );
        jdbc = new JdbcTemplate(source);
        jdbc.execute(
            "CREATE ALIAS UTC_TIMESTAMP FOR 'com.pluchon.meng.companion.CompanionRuntimeTest.utcTimestamp'"
        );
        var schema = new ClassPathResource(
            "naitang-schema.sql"
        ).getContentAsString(StandardCharsets.UTF_8);
        for (String statement : schema.split(";"))
            if (!statement.isBlank()) jdbc.execute(statement);
        var configuration = new org.apache.ibatis.session.Configuration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(CompanionMapper.class);
        configuration.addMapper(RuntimeMapper.class);
        configuration.addMapper(RuntimeV2Mapper.class);

        var factory = new SqlSessionFactoryBean();
        factory.setDataSource(source);
        factory.setConfiguration(configuration);
        var session = new SqlSessionTemplate(
            Objects.requireNonNull(factory.getObject())
        );
        var mapper = session.getMapper(CompanionMapper.class);
        var state = session.getMapper(
            com.pluchon.meng.companion.mapper.RuntimeV2Mapper.class
        );
        var runtime = spy(session.getMapper(RuntimeMapper.class));
        var transactions = new TransactionTemplate(
            new DataSourceTransactionManager(source)
        );
        var catalog = new CompanionCatalog();
        inject(catalog, "runtime", runtime);
        transactions.executeWithoutResult(status -> {
            try {
                catalog.run(null);
            } catch (Exception error) {
                throw new IllegalStateException(error);
            }
        });
        ai = mock(AiClient.class);
        limits = mock(CompanionLimits.class);
        outbox = spy(new CompanionOutbox());
        var redis = mock(
            org.springframework.data.redis.core.StringRedisTemplate.class
        );
        @SuppressWarnings("unchecked")
        org.springframework.data.redis.core.ValueOperations<
            String,
            String
        > cacheValues = mock(
            org.springframework.data.redis.core.ValueOperations.class
        );
        when(redis.opsForValue()).thenReturn(cacheValues);
        inject(outbox, "runtime", runtime, "limits", limits, "redis", redis);
        doNothing().when(outbox).enqueue(anyString(), anyString(), any());
        var clock = Clock.fixed(
            Instant.parse("2026-10-17T04:00:00Z"),
            ZoneOffset.UTC
        );
        var memoryCache = new CompanionMemoryCache(mapper, redis);
        var context = new CompanionContext();
        inject(
            context,
            "memoryCache",
            memoryCache,
            "runtime",
            runtime,
            "ai",
            ai,
            "clock",
            clock
        );
        var v2 = new CompanionRuntimeV2();
        inject(v2, "state", state, "memoryCache", memoryCache, "clock", clock);
        service = new CompanionService();
        inject(service, "snapshots", v2);
        inject(
            service,
            "mapper",
            mapper,
            "runtime",
            runtime,
            "context",
            context,
            "redis",
            redis,
            "clock",
            clock
        );
        executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(10);
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(6);
        executor.initialize();
        replies = new ReplyService();
        inject(replies, "v2", v2);
        inject(replies, "telemetry", mock(CompanionTelemetry.class));
        inject(
            replies,
            "companion",
            service,
            "mapper",
            mapper,
            "runtime",
            runtime,
            "limits",
            limits,
            "properties",
            new CompanionProperties(),
            "contexts",
            context,
            "outbox",
            outbox,
            "ai",
            ai,
            "users",
            mock(UserClient.class),
            "transactions",
            transactions,
            "companionExecutor",
            executor,
            "clock",
            clock
        );
        when(limits.tryLock(anyLong())).thenAnswer(invocation ->
            locks.add(invocation.getArgument(0)) ? "lease" : null
        );
        doAnswer(invocation -> {
            locks.remove(invocation.getArgument(0));
            return null;
        })
            .when(limits)
            .unlock(anyLong(), anyString());
        when(limits.activate(anyLong(), anyString())).thenReturn(true);
        when(limits.locked(anyLong(), anyString())).thenReturn(true);
        when(limits.reserve(anyLong(), anyString())).thenReturn(
            new CompanionLimits.Reservation(
                41,
                "2026-10-17",
                "r",
                List.of("message"),
                true
            )
        );
        when(limits.usage(anyLong(), anyString())).thenReturn(
            Map.of("messages", 199)
        );
        jdbc.update(
            "INSERT INTO companion_bond(user_id,character_code) VALUES(41,'NEKO')"
        );
        jdbc.update(
            "INSERT INTO companion_session(user_id,character_code,summary) VALUES(41,'NEKO','')"
        );
    }

    @AfterEach
    void close() {
        release.countDown();
        if (executor != null) executor.shutdown();
        jdbc.execute("DROP ALL OBJECTS");
    }

    private List<String> splitColumns(String value) {
        var parts = new ArrayList<String>();
        int depth = 0,
            start = 0;
        boolean quote = false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '\'') quote = !quote;
            if (quote) continue;
            if (c == '(') depth++;
            if (c == ')') depth--;
            if (c == ',' && depth == 0) {
                parts.add(value.substring(start, i).strip());
                start = i + 1;
            }
        }
        parts.add(value.substring(start).strip());
        return parts;
    }

    private void inject(Object target, Object... fields) {
        for (int i = 0; i < fields.length; i += 2) ReflectionTestUtils.setField(
            target,
            fields[i].toString(),
            fields[i + 1]
        );
    }

    private JsonNode result(String ignored) {
        return json.readTree(
            "{\"label\":\"PASS\",\"bubbles\":[\"歇会儿，喝点水喵。\"],\"billable\":true,\"metrics\":{},\"model\":\"recorded\",\"promptVersion\":\"test\"}"
        );
    }

    @SuppressWarnings("unchecked")
    private void record(String works) {
        when(ai.reply(anyMap(), any())).thenAnswer(invocation -> {
            requests.add(
                new LinkedHashMap<>(
                    (Map<String, Object>) invocation.getArgument(0)
                )
            );
            entered.countDown();
            assertTrue(release.await(5, TimeUnit.SECONDS));
            return result(works);
        });
    }

    private CompanionDTO.Send send(String id, String text) {
        return new CompanionDTO.Send(id, text, List.of());
    }

    private void until(java.util.function.BooleanSupplier condition)
        throws Exception {
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!condition.getAsBoolean() && System.nanoTime() < end)
            Thread.sleep(15);
        assertTrue(condition.getAsBoolean());
    }

    private int count(String condition) {
        return jdbc.queryForObject(
            "SELECT COUNT(*) FROM companion_message WHERE " + condition,
            Integer.class
        );
    }

    @Test
    void duplicateMessageAndDisconnectedSubscriberStillCommitExactlyOnce()
        throws Exception {
        record("[]");
        var first = replies.send(
            41,
            1,
            send("same", "今天有点累"),
            "Asia/Taipei",
            "test"
        );
        assertTrue(entered.await(5, TimeUnit.SECONDS));
        replies.send(41, 1, send("same", "今天有点累"), "Asia/Taipei", "test");
        first.setErrorResult(new IllegalStateException("browser disconnected"));
        release.countDown();
        until(() -> count("turn_state='COMMITTED'") == 1);
        assertEquals(1, count("role='USER'"));
        assertEquals(1, count("role='CHARACTER'"));
        verify(ai, times(1)).reply(anyMap(), any());
        replies.send(41, 1, send("same", "今天有点累"), "Asia/Taipei", "test");
        verify(ai, times(1)).reply(anyMap(), any());
    }

    @Test
    void queuedMessagesMergeIntoOneFollowingTurn() throws Exception {
        record("[]");
        replies.send(41, 1, send("first", "第一件事"), "Asia/Taipei", "test");
        assertTrue(entered.await(5, TimeUnit.SECONDS));
        replies.send(
            41,
            1,
            send("second", "还有第二件事"),
            "Asia/Taipei",
            "test"
        );
        replies.send(
            41,
            1,
            send("third", "还有第三件事"),
            "Asia/Taipei",
            "test"
        );
        release.countDown();
        until(() -> count("turn_state='COMMITTED'") == 3);
        assertEquals(2, requests.size());
        assertEquals(
            "还有第二件事\n还有第三件事",
            requests.get(1).get("content")
        );
        assertEquals(2, count("role='CHARACTER'"));
    }

    @Test
    void invalidBubbleRollsBackTheWholeReply() throws Exception {
        when(ai.reply(anyMap(), any())).thenReturn(
            json.readTree(
                "{\"label\":\"PASS\",\"bubbles\":[\"先写入\",\"\"],\"billable\":true}"
            )
        );
        replies.send(
            41,
            1,
            send("invalid-bubble", "普通输入"),
            "Asia/Taipei",
            "test"
        );
        until(() -> count("turn_state='FAILED'") == 1 && locks.isEmpty());
        assertEquals(0, count("role='CHARACTER'"));
        verify(limits).finish(any(), eq(List.of()));
    }

    @Test
    void clearingMemoryInvalidatesAnInflightReply() throws Exception {
        record("[]");
        replies.send(41, 1, send("reset", "喜欢蓝色"), "Asia/Taipei", "test");
        assertTrue(entered.await(5, TimeUnit.SECONDS));
        service.clearMemories(41);
        release.countDown();
        until(() -> count("turn_state='FAILED'") == 1);
        assertEquals(0, count("role='CHARACTER'"));
    }

    private Rows.Memory fact(String content) {
        var memory = new Rows.Memory();
        memory.setUserId(41L);
        memory.setCharacterCode("NEKO");
        memory.setContent(content);
        memory.setContentHash(content);
        memory.setSourceMessageId(1L);
        memory.setSourceSessionId(1L);
        return memory;
    }

    @Test
    void memoryInsertReturnsNewAndExistingIdentifiers() {
        var mapper = (CompanionMapper) ReflectionTestUtils.getField(
            replies,
            "mapper"
        );
        var first = fact("喜欢蓝色");
        mapper.insertMemory(first);
        assertNotNull(first.getId());
        var duplicate = fact("喜欢蓝色");
        mapper.insertMemory(duplicate);
        assertEquals(first.getId(), duplicate.getId());
        assertEquals(1, mapper.memories(41, "NEKO").size());
    }

    @Test
    void supersededFactRetainsAReferenceToItsReplacement() {
        var mapper = (CompanionMapper) ReflectionTestUtils.getField(
            replies,
            "mapper"
        );
        var runtime = (RuntimeMapper) ReflectionTestUtils.getField(
            replies,
            "runtime"
        );
        var first = fact("喜欢蓝色");
        var replacement = fact("现在喜欢红色");
        mapper.insertMemory(first);
        mapper.insertMemory(replacement);
        assertEquals(
            1,
            runtime.supersedeMemory(
                replacement.getId(),
                first.getId(),
                41,
                "NEKO"
            )
        );
        assertEquals(
            replacement.getId(),
            jdbc.queryForObject(
                "SELECT superseded_by FROM companion_memory WHERE id=?",
                Long.class,
                first.getId()
            )
        );
        var rememberedAgain = fact("喜欢蓝色");
        mapper.insertMemory(rememberedAgain);
        assertNotEquals(first.getId(), rememberedAgain.getId());
    }

    @Test
    void clearingMemoryAllowsRememberingTheSameFactAsANewRecord() {
        var mapper = (CompanionMapper) ReflectionTestUtils.getField(
            replies,
            "mapper"
        );
        var first = fact("喜欢蓝色");
        mapper.insertMemory(first);
        service.clearMemories(41);
        var rememberedAgain = fact("喜欢蓝色");
        mapper.insertMemory(rememberedAgain);
        assertNotEquals(first.getId(), rememberedAgain.getId());
        assertEquals(1, mapper.memories(41, "NEKO").size());
        assertEquals(
            2,
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM companion_memory",
                Integer.class
            )
        );
    }

    private CompanionBackground background(PrivateImageStorage ignored) {
        var value = new CompanionBackground();
        inject(
            value,
            "mapper",
            ReflectionTestUtils.getField(replies, "mapper"),
            "runtime",
            ReflectionTestUtils.getField(replies, "runtime"),
            "ai",
            ai,
            "transactions",
            ReflectionTestUtils.getField(replies, "transactions"),
            "outbox",
            outbox,
            "redis",
            mock(org.springframework.data.redis.core.StringRedisTemplate.class),
            "clock",
            Clock.fixed(Instant.parse("2026-10-17T04:00:00Z"), ZoneOffset.UTC),
            "v2",
            ReflectionTestUtils.getField(replies, "v2")
        );
        return value;
    }

    @Test
    void consolidationIsIdempotentAndRetainsOriginalSource() throws Exception {
        record("[]");
        release.countDown();
        replies.send(41, 1, send("fact", "喜欢蓝色"), "Asia/Taipei", "test");
        until(() -> count("turn_state='COMMITTED'") == 1 && locks.isEmpty());
        var result = json.readTree(
            "{\"reviewed\":true,\"decisions\":[{\"action\":\"ADD\",\"kind\":\"LIKE\",\"content\":\"喜欢蓝色\",\"evidence\":\"喜欢蓝色\",\"keywords\":[\"颜色\"],\"importance\":6,\"pinned\":false}],\"profile\":\"喜欢蓝色\"}"
        );
        when(ai.compute(eq("consolidations"), any())).thenReturn(result);
        when(ai.compute(eq("embeddings"), any())).thenReturn(
            json.readTree("{\"vectors\":[]}")
        );
        long input = jdbc.queryForObject(
            "SELECT id FROM companion_message WHERE role='USER'",
            Long.class
        );
        var event =
            new com.pluchon.meng.common.core.event.EventEnvelope<JsonNode>(
                "consolidate-test",
                "turn.committed",
                Instant.now(),
                "test",
                json.valueToTree(
                    Map.of(
                        "userId",
                        41,
                        "characterCode",
                        "NEKO",
                        "sessionId",
                        1,
                        "messageId",
                        input,
                        "revision",
                        0,
                        "timeZone",
                        "Asia/Taipei"
                    )
                )
            );
        var background = background(mock(PrivateImageStorage.class));
        background.consolidate(event);
        background.consolidate(event);
        assertEquals(
            1,
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM companion_memory",
                Integer.class
            )
        );
        assertEquals(
            input,
            jdbc.queryForObject(
                "SELECT source_message_id FROM companion_memory",
                Long.class
            )
        );
        assertEquals(
            1,
            jdbc.queryForObject(
                "SELECT source_session_id FROM companion_memory",
                Long.class
            )
        );
        verify(ai, times(1)).compute(eq("consolidations"), any());
    }

    @Test
    void invalidationClearsDerivedProfileEvenBelowRefreshThreshold()
        throws Exception {
        record("[]");
        release.countDown();
        replies.send(
            41,
            1,
            send("change", "不再喜欢蓝色"),
            "Asia/Taipei",
            "test"
        );
        until(() -> count("turn_state='COMMITTED'") == 1 && locks.isEmpty());
        jdbc.update(
            "UPDATE companion_bond SET profile='喜欢蓝色',profile_revision=1"
        );
        jdbc.update(
            "INSERT INTO companion_memory(user_id,character_code,content,content_hash,source_message_id,kind) VALUES(41,'NEKO','喜欢蓝色','old',1,'LIKE')"
        );
        long old = jdbc.queryForObject(
            "SELECT id FROM companion_memory",
            Long.class
        );
        when(ai.compute(eq("consolidations"), any())).thenReturn(
            json.readTree(
                "{\"reviewed\":true,\"decisions\":[{\"action\":\"INVALIDATE\",\"oldId\":" +
                    old +
                    ",\"kind\":\"LIKE\",\"evidence\":\"不再喜欢蓝色\"}],\"profile\":\"\"}"
            )
        );
        when(ai.compute(eq("embeddings"), any())).thenReturn(
            json.readTree("{\"vectors\":[]}")
        );
        long input = jdbc.queryForObject(
            "SELECT id FROM companion_message WHERE role='USER'",
            Long.class
        );
        var event =
            new com.pluchon.meng.common.core.event.EventEnvelope<JsonNode>(
                "invalidate-test",
                "turn.committed",
                Instant.now(),
                "test",
                json.valueToTree(
                    Map.of(
                        "userId",
                        41,
                        "characterCode",
                        "NEKO",
                        "sessionId",
                        1,
                        "messageId",
                        input,
                        "revision",
                        0,
                        "timeZone",
                        "Asia/Taipei"
                    )
                )
            );
        background(mock(PrivateImageStorage.class)).consolidate(event);
        assertEquals(
            "",
            jdbc.queryForObject(
                "SELECT profile FROM companion_bond",
                String.class
            )
        );
        assertEquals(
            1,
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM companion_memory WHERE invalid_at IS NOT NULL",
                Integer.class
            )
        );
    }

    @Test
    void timezoneAndMemoryReferenceBoundariesAreEnforced() {
        assertEquals(
            "Asia/Taipei",
            CompanionContext.zone("../../../etc/passwd")
        );
        assertEquals(
            "America/New_York",
            CompanionContext.zone("America/New_York")
        );
        var decision = json.readTree(
            "{\"reviewed\":true,\"decisions\":[{\"action\":\"INVALIDATE\",\"oldId\":99,\"kind\":\"LIFE\",\"evidence\":\"我改主意了\"}]}"
        );
        assertThrows(IllegalArgumentException.class, () ->
            CompanionBackground.validateDecisions(
                decision,
                List.of(),
                List.of()
            )
        );
        assertEquals(
            0,
            CompanionContext.cosine(
                json.readTree("[0,0]"),
                json.readTree("[1,0]")
            )
        );
    }

    @Test
    void readableCatalogIsIdempotentAndRetrievesSceneExamples()
        throws Exception {
        var runtime = (RuntimeMapper) ReflectionTestUtils.getField(
            replies,
            "runtime"
        );
        var catalog = new CompanionCatalog();
        inject(catalog, "runtime", runtime);
        catalog.run(null);
        assertEquals(
            1,
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM companion_card",
                Integer.class
            )
        );
        var bond = new com.pluchon.meng.companion.entity.Rows.Bond();
        bond.setCharacterCode("NEKO");
        var context = (CompanionContext) ReflectionTestUtils.getField(
            service,
            "context"
        );
        assertTrue(
            context.examples(bond, context.card(41, "NEKO")).size() >= 3
        );
        jdbc.update(
            "UPDATE companion_card SET active=FALSE WHERE character_code='NEKO'"
        );
        catalog.run(null);
        assertEquals(
            0,
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM companion_card WHERE character_code='NEKO' AND active=TRUE",
                Integer.class
            )
        );
        jdbc.update(
            "UPDATE companion_card SET content='{}' WHERE character_code='NEKO'"
        );
        assertThrows(IllegalStateException.class, () -> catalog.run(null));
    }

    @Test
    void completedCompactionsArePersistedOnceAndOldPressureCannotRepeat() {
        for (boolean capacity : List.of(false, true)) {
            jdbc.update("DELETE FROM companion_message");
            jdbc.update("DELETE FROM companion_runtime_daily");
            jdbc.update(
                "UPDATE companion_session SET window_start_message_id=0,summary_until_message_id=0,last_message_time=? WHERE id=1",
                capacity
                    ? LocalDateTime.parse("2026-10-17T04:00:00")
                    : LocalDateTime.parse("2026-10-17T03:00:00")
            );
            String metrics =
                "{\"windowStartMessageId\":0,\"layers\":{\"contextBudget\":{\"compactAfterRound\":" +
                capacity +
                "}}}";
            for (int i = 0; i < 20; i++) jdbc.update(
                "INSERT INTO companion_message(session_id,role,content,completed,metrics) VALUES(1,?,?,1,?)",
                i % 2 == 0 ? "USER" : "CHARACTER",
                "甲".repeat(1000),
                metrics
            );
            when(ai.summarize(anyString(), any())).thenReturn(null);
            var background = background(mock(PrivateImageStorage.class));
            background.summarize(41, 1, 0);
            assertEquals(
                0,
                jdbc.queryForObject(
                    "SELECT COUNT(*) FROM companion_runtime_daily",
                    Integer.class
                )
            );
            when(ai.summarize(anyString(), any())).thenReturn("归属明确的摘要");
            background.summarize(41, 1, 0);
            background.summarize(41, 1, 0);
            assertEquals(
                capacity ? 0 : 1,
                jdbc.queryForObject(
                    "SELECT idle_compactions FROM companion_runtime_daily",
                    Integer.class
                )
            );
            assertEquals(
                capacity ? 1 : 0,
                jdbc.queryForObject(
                    "SELECT capacity_compactions FROM companion_runtime_daily",
                    Integer.class
                )
            );
        }
    }

    @Test
    void unreviewedMemoryBatchCannotBeWritten() {
        assertThrows(IllegalArgumentException.class, () ->
            CompanionBackground.validateDecisions(
                json.readTree("{\"decisions\":[]}"),
                List.of(),
                List.of()
            )
        );
    }
}
