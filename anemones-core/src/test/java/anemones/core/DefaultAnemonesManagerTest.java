package anemones.core;

import anemones.core.event.AnemonesCompleteEvent;
import anemones.core.event.AnemonesInboundEvent;
import anemones.core.event.AnemonesStartEvent;
import anemones.core.support.ImportantWork;
import anemones.core.support.SimpleWork;
import anemones.core.util.TestHelper;
import io.lettuce.core.api.sync.RedisCommands;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Slf4j
@DisplayName("DefaultAnemonesManagerTest")
public class DefaultAnemonesManagerTest {


    public static final Map<String, Long> STARTED_JOB = new ConcurrentHashMap<>();

    @BeforeAll
    static void beforeAll() {
        ImportantWork importantWork = new ImportantWork();
        SimpleWork simpleWork = new SimpleWork();
        AnemonesConfig config = new AnemonesConfig();
        config.setNamespace("test");
        config.setConcurrency(2);
        config.setWorkers(Arrays.asList(importantWork, simpleWork));
        config.setRedisUrl(EmbeddedRedisExtension.REDIS_URI);
        config.setConverter(TestHelper.CONVERTER);
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
    public void executorTest() throws InterruptedException {
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
        manager.submitTask("simple", Arrays.asList("job6_1", "job7_1"));
        manager.submitTask("important", Arrays.asList("job4_1", "job5_1"));
        manager.submitIn("important", Collections.singletonList("job3_1"),
                2, TimeUnit.SECONDS);
        long now = System.currentTimeMillis();
        while (STARTED_JOB.size() < 7 && System.currentTimeMillis() - now < 20_000) {
            TimeUnit.SECONDS.sleep(1);
        }
        String rescueJob = "job8_10";
        manager.submitTask("simple", Collections.singletonList(rescueJob));
        TimeUnit.SECONDS.sleep(1);
        manager.close();
        log.info("STARTED_JOB: {}", STARTED_JOB);
        for (String job : Arrays.asList("job1", "job2", "job3", "job4", "job5", "job6", "job7")) {
            Assertions.assertTrue(STARTED_JOB.containsKey(job), "任务" + job + "执行完成");
        }
        Assertions.assertTrue(STARTED_JOB.get("job3") <= STARTED_JOB.get("job4"), "定时任务到点需要优先执行");
        Assertions.assertTrue(STARTED_JOB.get("job6") > STARTED_JOB.get("job3"), "权重高的任务必须优先执行");
        RedisCommands<String, String> commands = EmbeddedRedisExtension.REDIS_CONN.sync();
        String param = commands.rpop(manager.getAnemonesKeyCache("simple").getListKey());
        Assertions.assertNotNull(param, "救援需要重新把数据放回redis");
        AnemonesData data = TestHelper.CONVERTER.deserialize(param);
        Assertions.assertEquals(rescueJob, data.getParam(), "救援的任务需要是原任务");
    }


}