package com.pluchon.meng.companion;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.pluchon.meng.companion.client.*;
import com.pluchon.meng.companion.config.CompanionProperties;
import com.pluchon.meng.companion.controller.CompanionDTO;
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
        for (String file : List.of(
            "V20261017001__create_companion.sql",
            "V20261017002__companion_runtime.sql"
        )) {
            String sql = new ClassPathResource(
                "db/migration/" + file
            ).getContentAsString(StandardCharsets.UTF_8);
            for (String statement : sql.split(";")) {
                String value = statement.strip().replace(" JSON", " CLOB");
                if (
                    value.isBlank() ||
                    value.startsWith("UPDATE companion_memory m JOIN")
                ) continue;
                if (value.startsWith("ALTER TABLE")) {
                    var match = java.util.regex.Pattern.compile(
                        "ALTER TABLE (\\w+)\\s+(.*)",
                        java.util.regex.Pattern.DOTALL
                    ).matcher(value);
                    assertTrue(match.matches());
                    for (String alteration : splitColumns(match.group(2)))
                        jdbc.execute(
                            "ALTER TABLE " + match.group(1) + " " + alteration
                        );
                } else jdbc.execute(value);
            }
        }
        var configuration = new org.apache.ibatis.session.Configuration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(CompanionMapper.class);
        configuration.addMapper(RuntimeMapper.class);
        var factory = new SqlSessionFactoryBean();
        factory.setDataSource(source);
        factory.setConfiguration(configuration);
        var session = new SqlSessionTemplate(
            Objects.requireNonNull(factory.getObject())
        );
        var mapper = session.getMapper(CompanionMapper.class);
        var runtime = spy(session.getMapper(RuntimeMapper.class));
        doReturn(null).when(runtime).feeling(anyLong());
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
        inject(outbox, "runtime", runtime, "limits", limits, "redis", redis);
        doNothing().when(outbox).enqueue(anyString(), anyString(), any());
        var clock = Clock.fixed(
            Instant.parse("2026-10-17T04:00:00Z"),
            ZoneOffset.UTC
        );
        var context = new CompanionContext();
        inject(
            context,
            "mapper",
            mapper,
            "runtime",
            runtime,
            "ai",
            ai,
            "clock",
            clock
        );
        service = new CompanionService();
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
            clock,
            "properties",
            new CompanionProperties(),
            "ai",
            ai,
            "transactions",
            transactions
        );
        executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(10);
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(6);
        executor.initialize();
        replies = new ReplyService();
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
                List.of("message", "document", "image"),
                true
            )
        );
        when(limits.tools(any())).thenReturn(
            List.of(
                "search_posts",
                "write_document",
                "revise_work",
                "draw_picture"
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

    private JsonNode result(String works) {
        return json.readTree(
            "{\"label\":\"PASS\",\"bubbles\":[\"歇会儿，喝点水喵。\"],\"works\":" +
                works +
                ",\"drawings\":[],\"affection\":3,\"billable\":true,\"metrics\":{},\"model\":\"recorded\",\"promptVersion\":\"test\"}"
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
        return new CompanionDTO.Send(id, text, List.of(), null);
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
    void malformedWorkRollsBackBubblesAndAffection() throws Exception {
        record("[{\"kind\":\"DOCUMENT\",\"title\":\"\",\"body\":\"正文\"}]");
        replies.send(41, 1, send("invalid", "写点东西"), "Asia/Taipei", "test");
        release.countDown();
        until(() -> count("turn_state='FAILED'") == 1);
        assertEquals(0, count("role='CHARACTER'"));
        assertEquals(
            0,
            jdbc.queryForObject(
                "SELECT affection FROM companion_bond WHERE user_id=41",
                Integer.class
            )
        );
        verify(limits).finish(any(), eq(List.of()));
    }

    @Test
    void clearingMemoryInvalidatesAnInflightReply() throws Exception {
        record("[]");
        replies.send(41, 1, send("reset", "喜欢蓝色"), "Asia/Taipei", "test");
        assertTrue(entered.await(5, TimeUnit.SECONDS));
        service.clearMemories(41, "NEKO");
        release.countDown();
        until(() -> count("turn_state='FAILED'") == 1);
        assertEquals(0, count("role='CHARACTER'"));
    }

    @Test
    void regenerationReplacesOneRoundWithoutRepeatingAffection()
        throws Exception {
        record("[]");
        release.countDown();
        replies.send(
            41,
            1,
            send("original", "今天有点累"),
            "Asia/Taipei",
            "test"
        );
        until(() -> count("turn_state='COMMITTED'") == 1 && locks.isEmpty());
        long old = jdbc.queryForObject(
            "SELECT id FROM companion_message WHERE role='CHARACTER'",
            Long.class
        );
        int affection = jdbc.queryForObject(
            "SELECT affection FROM companion_bond WHERE user_id=41",
            Integer.class
        );
        replies.regenerate(41, old, "Asia/Taipei", "test");
        until(() -> count("turn_state='COMMITTED'") == 2 && locks.isEmpty());
        assertEquals(1, count("role='USER' AND delete_state=0"));
        assertEquals(1, count("role='CHARACTER' AND delete_state=0"));
        assertEquals(
            affection,
            jdbc.queryForObject(
                "SELECT affection FROM companion_bond WHERE user_id=41",
                Integer.class
            )
        );
        assertEquals(
            1,
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM companion_feedback WHERE replacement_message_id IS NOT NULL",
                Integer.class
            )
        );
        long replacement = jdbc.queryForObject(
            "SELECT id FROM companion_message WHERE role='CHARACTER' AND delete_state=0",
            Long.class
        );
        assertThrows(
            com.pluchon.meng.common.core.error.BusinessException.class,
            () -> replies.regenerate(41, replacement, "Asia/Taipei", "test")
        );
    }

    private CompanionBackground background(WorkStorage storage) {
        var service = new CompanionBackground();
        inject(
            service,
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
            "storage",
            storage,
            "limits",
            limits,
            "redis",
            mock(org.springframework.data.redis.core.StringRedisTemplate.class),
            "clock",
            Clock.fixed(Instant.parse("2026-10-17T04:00:00Z"), ZoneOffset.UTC)
        );
        return service;
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
        var background = background(mock(WorkStorage.class));
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
    void promiseNotificationUsesLocalDayAndOnlyPublishesOnce() {
        jdbc.update("UPDATE companion_bond SET time_zone='Pacific/Honolulu'");
        jdbc.update(
            "INSERT INTO companion_memory(user_id,character_code,content,content_hash,source_message_id,kind,due_date) VALUES(41,'NEKO','今天看展','p',1,'PROMISE','2026-10-17')"
        );
        var background = background(mock(WorkStorage.class));
        background.promises();
        assertEquals(
            0,
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM companion_memory WHERE notified=1",
                Integer.class
            )
        );
        jdbc.update("UPDATE companion_bond SET time_zone='Asia/Taipei'");
        background.promises();
        background.promises();
        assertEquals(
            1,
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM companion_memory WHERE notified=1",
                Integer.class
            )
        );
        verify(outbox, times(1)).enqueue(
            eq("companion.promise-due"),
            anyString(),
            any()
        );
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
        background(mock(WorkStorage.class)).consolidate(event);
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
    void backgroundDrawingPublishesAndChargesExactlyOnce() throws Exception {
        record("[]");
        release.countDown();
        replies.send(41, 1, send("draw", "画朵云"), "Asia/Taipei", "test");
        until(() -> count("turn_state='COMMITTED'") == 1 && locks.isEmpty());
        long input = jdbc.queryForObject(
            "SELECT id FROM companion_message WHERE role='USER'",
            Long.class
        );
        var reservation = new CompanionLimits.Reservation(
            41,
            "2026-10-17",
            "image-r",
            List.of("image"),
            false
        );
        jdbc.update(
            "INSERT INTO companion_drawing(user_id,session_id,message_id,instruction,reservation,revision) VALUES(41,1,?,'一朵云',?,0)",
            input,
            json.writeValueAsString(reservation)
        );
        var storage = mock(WorkStorage.class);
        when(storage.save(any())).thenReturn(
            new WorkStorage.Saved("saved.png", "thumb")
        );
        when(ai.draw(anyString())).thenReturn(new byte[] { 1 });
        var event =
            new com.pluchon.meng.common.core.event.EventEnvelope<JsonNode>(
                "draw-test",
                "drawing.requested",
                Instant.now(),
                "test",
                json.valueToTree(Map.of("messageId", input))
            );
        var background = background(storage);
        background.draw(event);
        background.draw(event);
        assertEquals(
            "COMPLETED",
            jdbc.queryForObject(
                "SELECT status FROM companion_drawing",
                String.class
            )
        );
        assertEquals(
            1,
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM companion_work",
                Integer.class
            )
        );
        verify(limits, times(1)).finish(eq(reservation), eq(List.of("image")));
        verify(outbox, times(1)).enqueue(
            eq("companion.drawing-finished"),
            anyString(),
            any()
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
            3,
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM companion_card",
                Integer.class
            )
        );
        assertEquals(
            180,
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM companion_line",
                Integer.class
            )
        );
        assertEquals(
            45,
            jdbc
                .queryForList(
                    "SELECT character_code,level,stage,COUNT(*) FROM companion_line GROUP BY character_code,level,stage HAVING COUNT(*)>=3"
                )
                .size()
        );
        var bond = new com.pluchon.meng.companion.entity.Rows.Bond();
        bond.setCharacterCode("TSUNDERE");
        bond.setLevel(2);
        bond.setStage(1);
        var context = (CompanionContext) ReflectionTestUtils.getField(
            service,
            "context"
        );
        var examples = context.examples(bond, context.card(41, "TSUNDERE"));
        assertEquals(4, examples.size());
        assertTrue(
            examples
                .stream()
                .anyMatch(item ->
                    "作业写不动".equals(
                        item.path("dialogue").path("user").asString()
                    )
                )
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
    void fullRolloutRetiresOlderCardsButPartialRolloutKeepsFallback()
        throws Exception {
        var runtime = (RuntimeMapper) ReflectionTestUtils.getField(
            replies,
            "runtime"
        );
        jdbc.update(
            "INSERT INTO companion_card(id,character_code,version,content,active,rollout_percent) VALUES(-1,'TSUNDERE','legacy','{\"version\":\"legacy\"}',TRUE,100)"
        );
        jdbc.update(
            "UPDATE companion_card SET rollout_percent=50 WHERE character_code='TSUNDERE' AND version<>'legacy'"
        );
        var catalog = new CompanionCatalog();
        inject(catalog, "runtime", runtime);
        catalog.run(null);
        assertEquals(
            "legacy",
            json
                .readTree(runtime.card("TSUNDERE", 77))
                .path("version")
                .asString()
        );
        assertNotEquals(
            "legacy",
            json
                .readTree(runtime.card("TSUNDERE", 41))
                .path("version")
                .asString()
        );
        jdbc.update(
            "UPDATE companion_card SET rollout_percent=100 WHERE character_code='TSUNDERE' AND version<>'legacy'"
        );
        catalog.run(null);
        assertEquals(
            0,
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM companion_card WHERE version='legacy' AND active=TRUE",
                Integer.class
            )
        );
        assertNotEquals(
            "legacy",
            json
                .readTree(runtime.card("TSUNDERE", 77))
                .path("version")
                .asString()
        );
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

    @Test
    void manualMemoryEditsRequireSemanticApprovalAndOwnership() {
        jdbc.update(
            "INSERT INTO companion_memory(id,user_id,character_code,content,content_hash,source_message_id) VALUES(9001,41,'NEKO','喜欢蓝色',REPEAT('a',64),1)"
        );
        var edit = new CompanionDTO.Memory("研究电话号码脱敏", null);
        assertThrows(
            com.pluchon.meng.common.core.error.BusinessException.class,
            () -> service.editMemory(42, 9001, edit)
        );
        verify(ai, never()).compute(eq("memory-reviews"), any());
        when(ai.compute(eq("memory-reviews"), any())).thenReturn(
            json.readTree("{\"safe\":true}")
        );
        assertThrows(
            com.pluchon.meng.common.core.error.BusinessException.class,
            () -> service.editMemory(41, 9001, edit)
        );
        when(ai.compute(eq("memory-reviews"), any())).thenReturn(
            json.readTree("{\"safe\":false,\"reviewed\":true}")
        );
        assertThrows(
            com.pluchon.meng.common.core.error.BusinessException.class,
            () -> service.editMemory(41, 9001, edit)
        );
        assertEquals(
            "喜欢蓝色",
            jdbc.queryForObject(
                "SELECT content FROM companion_memory WHERE id=9001",
                String.class
            )
        );
        when(ai.compute(eq("memory-reviews"), any())).thenReturn(
            json.readTree("{\"safe\":true,\"reviewed\":true}")
        );
        service.editMemory(41, 9001, edit);
        assertEquals(
            "研究电话号码脱敏",
            jdbc.queryForObject(
                "SELECT content FROM companion_memory WHERE id=9001",
                String.class
            )
        );
    }
}
