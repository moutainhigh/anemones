package anemones.core;

import anemones.core.middleware.retry.AnemonesRetryListener;
import anemones.core.support.RetryTask;
import anemones.core.util.TestHelper;
import io.lettuce.core.api.sync.RedisCommands;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

@SpringBootTest(classes = MiddlewareTest.TestConfig.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Slf4j
@DisplayName("MiddlewareTest")
public class MiddlewareTest {

    private static volatile boolean errConsumer = false;

    @SpringBootConfiguration
    @Slf4j
    public static class TestConfig {


        @Bean
        public RetryTask retryWorker() {
            return new RetryTask();
        }

        @Bean
        public DefaultAnemonesManager sidekiqManager(List<AnemonesWorker> workers) {
            AnemonesConfig config = new AnemonesConfig();
            config.setNamespace("test");
            config.setConcurrency(2);
            config.setWorkers(workers);
            config.setRedisUrl(EmbeddedRedisExtension.REDIS_URI);
            config.setConverter(TestHelper.CONVERTER);
            AnemonesRetryListener listener = new AnemonesRetryListener((data, throwable) -> errConsumer = true);
            config.setListeners(Collections.singletonList(listener));
            config.setWaitSecondsToTerminate(1);
            return new DefaultAnemonesManager(config);
        }
    }


    @Autowired
    private DefaultAnemonesManager sidekiqManager;

    @Test
    @DisplayName("retry middleware")
    public void retry() throws InterruptedException {
        RedisCommands<String, String> commands = EmbeddedRedisExtension.REDIS_CONN.sync();
        AnemonesKeyCache cache = sidekiqManager.getAnemonesKeyCache(RetryTask.RETRY_QUEUE);
        commands.del(cache.getZsetKey());
        sidekiqManager.submitTask(RetryTask.RETRY_QUEUE, Collections.singletonList(""));
        TimeUnit.SECONDS.sleep(1);

        List<String> param = commands.zrange(cache.getZsetKey(), 0, -1);
        Assertions.assertEquals(param.size(), 1, "任务发生错误需要放回任务队列");
        AnemonesData datum = TestHelper.CONVERTER.deserialize(param.get(0));
        Assertions.assertEquals(datum.getRetry(), 1, "任务发生错误时需要增加retry次数");
        long now = System.currentTimeMillis();
        while (commands.zcard(cache.getZsetKey()) != 0 && System.currentTimeMillis() - now < 60_000) {
            TimeUnit.SECONDS.sleep(1);
        }
        Assertions.assertTrue(System.currentTimeMillis() - now > 16_000, "重试至少间隔16秒");
        TimeUnit.SECONDS.sleep(1);
        Assertions.assertTrue(errConsumer, "任务错误次数耗尽需要走到消费者中");
    }


}