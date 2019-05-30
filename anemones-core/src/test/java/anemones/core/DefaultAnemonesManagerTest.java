package anemones.core;

import anemones.core.event.AnemonesCompleteEvent;
import anemones.core.event.AnemonesInboundEvent;
import anemones.core.event.AnemonesStartEvent;
import anemones.core.support.ImportantWork;
import anemones.core.support.SimpleWork;
import anemones.core.util.TestHelper;
import io.lettuce.core.api.sync.RedisCommands;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@SpringBootTest(classes = DefaultAnemonesManagerTest.TestConfig.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Slf4j
@DisplayName("DefaultAnemonesManagerTest")
public class DefaultAnemonesManagerTest {


    public static final Map<String, Long> STARTED_JOB = new ConcurrentHashMap<>();


    @AfterEach
    void clearStartedJob() {
        STARTED_JOB.clear();
    }

    @SpringBootConfiguration
    @Slf4j
    public static class TestConfig {


        @Bean
        public AnemonesWorker importantWork() {
            return new ImportantWork();
        }

        @Bean
        public AnemonesWorker simpleWorker() {
            return new SimpleWork();
        }

        @Bean
        public DefaultAnemonesManager sidekiqManager(List<AnemonesWorker> workers) {
            AnemonesConfig config = new AnemonesConfig();
            config.setNamespace("test");
            config.setConcurrency(2);
            config.setWorkers(workers);
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
            return new DefaultAnemonesManager(config);
        }
    }


    @Autowired
    private DefaultAnemonesManager sidekiqManager;

    @Test
    public void executorTest() throws InterruptedException {
        AnemonesThreadPoolExecutor e = new AnemonesThreadPoolExecutor(1);
        AnemonesData data = new AnemonesData();
        data.setParam("test_1");
        SimpleWork simpleWork = new SimpleWork();
        WorkerRunnable work = new WorkerRunnable(sidekiqManager, simpleWork, data);
        e.execute(work);
        TimeUnit.MILLISECONDS.sleep(100);
        Assertions.assertEquals(e.getRunningWorkers().size(), 1, "添加任务需要保存");
        Assertions.assertEquals(work, e.getRunningWorkers().iterator().next(), "必须能够获取到添加的任务");
        e.shutdown();
    }

    @Test
    @DisplayName("integrated test")
    void integrated() throws Exception {
        sidekiqManager.submitTask("important", Arrays.asList("job1_10", "job2_10"));
        sidekiqManager.submitTask("simple", Arrays.asList("job6_1", "job7_1"));
        sidekiqManager.submitTask("important", Arrays.asList("job4_1", "job5_1"));
        sidekiqManager.submitIn("important", Collections.singletonList("job3_1"),
                2, TimeUnit.SECONDS);
        long now = System.currentTimeMillis();
        while (STARTED_JOB.size() < 7 && System.currentTimeMillis() - now < 20_000) {
            TimeUnit.SECONDS.sleep(1);
        }
        String rescueJob = "job8_10";
        sidekiqManager.submitTask("simple", Collections.singletonList(rescueJob));
        TimeUnit.SECONDS.sleep(1);
        sidekiqManager.destroy();
        log.info("STARTED_JOB: {}", STARTED_JOB);
        for (String job : Arrays.asList("job1", "job2", "job3", "job4", "job5", "job6", "job7")) {
            Assertions.assertTrue(STARTED_JOB.containsKey(job), "任务" + job + "执行完成");
        }
        Assertions.assertTrue(STARTED_JOB.get("job3") <= STARTED_JOB.get("job4"), "定时任务到点需要优先执行");
        Assertions.assertTrue(STARTED_JOB.get("job6") > STARTED_JOB.get("job3"), "权重高的任务必须优先执行");
        RedisCommands<String, String> commands = EmbeddedRedisExtension.REDIS_CONN.sync();
        String param = commands.rpop(sidekiqManager.getAnemonesKeyCache("simple").getListKey());
        Assertions.assertNotNull(param, "救援需要重新把数据放回redis");
        AnemonesData data = TestHelper.CONVERTER.deserialize(param);
        Assertions.assertEquals(rescueJob, data.getParam(), "救援的任务需要是原任务");
    }


}