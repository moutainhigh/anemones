package anemones.core;

import anemones.core.extension.EmbeddedRedisExtension;
import anemones.core.retry.AnemonesRetryListener;
import anemones.core.util.TestConstants;
import io.lettuce.core.api.sync.RedisCommands;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@DisplayName("MiddlewareTest")
public class MiddlewareTest {

    private static volatile boolean errConsumer = false;

    public static final String RETRY_QUEUE = "retry";
    public static final String NO_RETRY_QUEUE = "no_retry";
    public static final String ABANDON_QUEUE = "abandon";

    private static DefaultAnemonesManager manager;

    static class RetryTask implements AnemonesWorker {


        @Override
        public int retry() {
            return 1;
        }

        @Override
        public String queue() {
            return RETRY_QUEUE;
        }

        @Override
        public Object perform(String param) {
            throw new IllegalStateException("失败");
        }
    }

    @BeforeAll
    static void beforeAll() {
        AnemonesConfig config = new AnemonesConfig();
        config.setNamespace("test");
        config.setConcurrency(2);
        config.setWorkers(Arrays.asList(new RetryTask(), new AnemonesWorker() {
            @Override
            public String queue() {
                return NO_RETRY_QUEUE;
            }

            @Override
            public Object perform(String param) {
                throw new IllegalStateException("失败");
            }
        }, new AnemonesWorker() {
            @Override
            public String queue() {
                return ABANDON_QUEUE;
            }

            @Override
            public Object perform(String param) {
                throw new AnemonesAbandonException("give up");
            }
        }));
        config.setRedisUrl(EmbeddedRedisExtension.REDIS_URI);
        config.setConverter(TestConstants.CONVERTER);
        AnemonesRetryListener listener = new AnemonesRetryListener((data, throwable) -> errConsumer = true);
        config.setListeners(Collections.singletonList(listener));
        config.setWaitSecondsToTerminate(1);
        manager = new DefaultAnemonesManager(config);
        manager.init();
    }

    @AfterAll
    static void afterAll() throws Exception {
        manager.close();
    }


    @Test
    @DisplayName("retry middleware")
    void retry() throws InterruptedException {
        RedisCommands<String, String> commands = EmbeddedRedisExtension.REDIS_CONN.sync();
        AnemonesKeyCache cache = manager.getAnemonesKeyCache(RETRY_QUEUE);
        commands.del(cache.getZsetKey());
        manager.submitTask(RETRY_QUEUE, Collections.singletonList(""));
        TimeUnit.SECONDS.sleep(1);

        List<String> param = commands.zrange(cache.getZsetKey(), 0, -1);
        Assertions.assertEquals(param.size(), 1, "The task need to be pushed back queue after throwing exception");
        AnemonesData datum = TestConstants.CONVERTER.deserialize(param.get(0));
        Assertions.assertEquals(datum.getRetry(), 1, "The retry count need to increment after throwing exception");
        long now = System.currentTimeMillis();
        while (commands.zcard(cache.getZsetKey()) != 0 && System.currentTimeMillis() - now < 60_000) {
            TimeUnit.SECONDS.sleep(1);
        }
        Assertions.assertTrue(System.currentTimeMillis() - now > 16_000, "Retry interval takes at least sixteen seconds(no reason)");
        log.info("left keys : {}", commands.keys("*"));
        TimeUnit.SECONDS.sleep(3);
        Assertions.assertTrue(errConsumer, "When retry count reach the max retry limit, error consumer must be notified");

        errConsumer = false;
        manager.submitTask(ABANDON_QUEUE, Collections.singletonList(""));
        TimeUnit.SECONDS.sleep(1);
        Assertions.assertEquals(0, commands.zcard(cache.getZsetKey()), "It will not have any further action when throwing " + AnemonesAbandonException.class.getName());
        Assertions.assertFalse(errConsumer, "Error consumer will not be notified when throwing " + AnemonesAbandonException.class.getName());

        manager.submitTask(NO_RETRY_QUEUE, Collections.singletonList(""));
        TimeUnit.SECONDS.sleep(1);
        Assertions.assertEquals(0, commands.zcard(cache.getZsetKey()), "It will not have any further action when there isn't retry config");
        Assertions.assertFalse(errConsumer, "Error consumer will not be notified when there isn't retry config");
    }


}