package anemones.core;

import anemones.core.event.AnemonesCompleteEvent;
import anemones.core.event.AnemonesInboundEvent;
import anemones.core.event.AnemonesStartEvent;
import anemones.core.extension.EmbeddedRedisExtension;
import anemones.core.support.ImportantWork;
import anemones.core.support.SimpleWork;
import anemones.core.util.TestConstants;
import io.lettuce.core.api.sync.RedisCommands;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static anemones.core.extension.EmbeddedRedisExtension.REDIS_CONN;

/**
 * 基础集成测试
 */
@Slf4j
@DisplayName("DefaultAnemonesManagerTest")
public class DefaultAnemonesManagerTest {


    public static final Map<String, Long> STARTED_JOB = Collections.synchronizedMap(new LinkedHashMap<>());

    @BeforeAll
    static void beforeAll() {
        ImportantWork importantWork = new ImportantWork();
        SimpleWork simpleWork = new SimpleWork();
        AnemonesConfig config = new AnemonesConfig();
        config.setNamespace("test");
        config.setConcurrency(2);
        config.setWorkers(Arrays.asList(importantWork, simpleWork));
        config.setRedisUrl(EmbeddedRedisExtension.REDIS_URI);
        config.setConverter(TestConstants.CONVERTER);
        config.setListeners(Collections.singletonList((manager, event) -> {
            if (event instanceof AnemonesStartEvent) {
                log.info("任务开始: {}", event.getPayload());
            } else if (event instanceof AnemonesInboundEvent) {
                log.info("添加任务: {}", event.getPayload());
            } else if (event instanceof AnemonesCompleteEvent) {
                Throwable throwable = ((AnemonesCompleteEvent) event).getThrowable();
                if (throwable != null) {
                    log.error("任务完成 异常: {}", event.getPayload(), throwable);
                } else {
                    log.info("任务完成 成功: {}", event.getPayload());
                }
            }
        }));
        config.setWaitSecondsToTerminate(1);
        manager = new DefaultAnemonesManager(config);
        manager.init();
    }

    @AfterAll
    static void clearAll() throws Exception {
        manager.close();
    }

    @AfterEach
    void clearStartedJob() {
        STARTED_JOB.clear();
    }

    private static DefaultAnemonesManager manager;

    @Test
    void testConfig() throws Exception {
        AnemonesConfig config = new AnemonesConfig();
        IllegalArgumentException e = Assertions.assertThrows(IllegalArgumentException.class, () -> {
            new DefaultAnemonesManager(config);
        });
        Assertions.assertTrue(e.getMessage().contains("redisUrl"));
        config.setNamespace("test");
        config.setConcurrency(0);
        config.setRedisUrl(EmbeddedRedisExtension.REDIS_URI);
        config.setWaitSecondsToTerminate(-1);
        DefaultAnemonesManager m = new DefaultAnemonesManager(config);
        Assertions.assertEquals(0, m.getWaitSecondsToTerminate());
        m = new DefaultAnemonesManager(config);
        try {
            e = Assertions.assertThrows(IllegalArgumentException.class, m::init);
            Assertions.assertTrue(e.getMessage().contains("converter"));
        } finally {
            m.close();
        }
        try {
            config.setWorkers(Collections.singletonList(new SimpleWork()));
            config.setConverter(TestConstants.CONVERTER);
            m = new DefaultAnemonesManager(config);
            e = Assertions.assertThrows(IllegalArgumentException.class, m::init);
            Assertions.assertTrue(e.getMessage().contains("concurrency"));
        } finally {
            m.close();
        }
        try {
            config.setWorkers(Collections.emptyList());
            m = new DefaultAnemonesManager(config);
            Assertions.assertDoesNotThrow(m::init, "When there is no workers, Anemones will not create thread pool.");
        } finally {
            m.close();
        }
    }

    @Test
    void fireEventTest() {
        Assertions.assertDoesNotThrow(() -> manager.fireEvent(new AnemonesInboundEvent(null, null, null, false)),
                "Listener's error will be caught");
    }

    @Test
    void executorTest() throws InterruptedException {
        AnemonesThreadPoolExecutor e = new AnemonesThreadPoolExecutor(1);
        AnemonesData data = new AnemonesData();
        data.setParam("test_1");
        SimpleWork simpleWork = new SimpleWork();
        WorkerRunnable work = new WorkerRunnable(manager, simpleWork, data);
        e.execute(work);
        TimeUnit.MILLISECONDS.sleep(100);
        Assertions.assertEquals(e.getRunningWorkers().size(), 1, "添加任务需要保存");
        Assertions.assertEquals(work, e.getRunningWorkers().iterator().next(), "必须能够获取到添加的任务");
        e.shutdown();
    }

    @Test
    @DisplayName("integrated test")
    void integrated() throws Exception {
        manager.submitTask("important", Arrays.asList("job1_10", "job2_10"));
        manager.submitTask("simple", Arrays.asList("job7_1", "job8_1"));
        manager.submitTask("important", Arrays.asList("job6_1", "job5_1"));
        manager.submitIn("important", Arrays.asList("job3_1", "job4_1"),
                2, TimeUnit.SECONDS);
        long now = System.currentTimeMillis();
        while (STARTED_JOB.size() < 7 && System.currentTimeMillis() - now < 20_000) {
            TimeUnit.SECONDS.sleep(1);
        }
        String rescueJob = "jobrescue_10";
        manager.submitTask("simple", Collections.singletonList(rescueJob));
        TimeUnit.SECONDS.sleep(1);
        manager.close();
        log.info("STARTED_JOB: {}", STARTED_JOB.entrySet());
        for (String job : Arrays.asList("job1", "job2", "job3", "job4", "job5", "job6", "job7", "job8")) {
            Assertions.assertTrue(STARTED_JOB.containsKey(job), "任务" + job + "执行完成");
        }
        Assertions.assertTrue(STARTED_JOB.get("job3") <= STARTED_JOB.get("job5"), "定时任务到点需要优先执行");
        Assertions.assertTrue(STARTED_JOB.get("job7") > STARTED_JOB.get("job3"), "权重高的任务必须优先执行");
        RedisCommands<String, String> commands = REDIS_CONN.sync();
        log.info("当前剩余的key{}", commands.keys("*"));
        String param = commands.rpop(manager.getAnemonesKeyCache("simple").getListKey());
        Assertions.assertNotNull(param, "救援需要重新把数据放回redis");
        AnemonesData data = TestConstants.CONVERTER.deserialize(param);
        Assertions.assertEquals(rescueJob, data.getParam(), "救援的任务需要是原任务");
        commands.flushall();
    }


}