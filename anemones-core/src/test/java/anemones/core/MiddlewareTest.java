package anemones.core;

import anemones.core.retry.AnemonesRetryListener;
import anemones.core.support.RetryTask;
import anemones.core.util.TestHelper;
import com.alibaba.fastjson.JSON;
import io.lettuce.core.api.sync.RedisCommands;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@DisplayName("MiddlewareTest")
public class MiddlewareTest {

    private static volatile boolean errConsumer = false;

    private static DefaultAnemonesManager manager;

    @BeforeAll
    static void beforeAll() {
        AnemonesConfig config = new AnemonesConfig();
        config.setNamespace("test");
        config.setConcurrency(2);
        config.setWorkers(Collections.singletonList(new AnemonesWorker() {
            @Override
            public String queue() {
                return "test_queue";
            }

            @Override
            public Object perform(String param) {

                int result = Integer.parseInt(param) * 2;
                System.out.printf("%s * 2 = %d \n", param, result);
                return result;
            }
        }));
        config.setRedisUrl(EmbeddedRedisExtension.REDIS_URI);
        config.setConverter(new AnemonesParamConverter() {
            @Override
            public String serialize(AnemonesData object) {
                // you can use "fastjson" or other serializer and deserializer
                return JSON.toJSONString(object);
            }

            @Override
            public AnemonesData deserialize(String param) {
                return JSON.parseObject(param, AnemonesData.class);
            }
        });
        AnemonesRetryListener listener = new AnemonesRetryListener((data, throwable) -> errConsumer = true);
        config.setListeners(Collections.singletonList(listener));
        config.setWaitSecondsToTerminate(1);
        manager = new DefaultAnemonesManager(config);
        manager.init();

        // will print "10 * 2 = 20"
        manager.submitTask("test_queue", Collections.singletonList("10"));

        // will print "20 * 2 = 40" at least 10 seconds later
        manager.submitIn("test_queue", Collections.singletonList("20"), 10, TimeUnit.SECONDS);

        try {
            manager.close();
        } catch (Exception e) {
            //
        }
    }

    @AfterAll
    static void afterAll() throws Exception {
        manager.close();
    }


    @Test
    @DisplayName("retry middleware")
    void retry() throws InterruptedException {
        RedisCommands<String, String> commands = EmbeddedRedisExtension.REDIS_CONN.sync();
        AnemonesKeyCache cache = manager.getAnemonesKeyCache(RetryTask.RETRY_QUEUE);
        commands.del(cache.getZsetKey());
        manager.submitTask(RetryTask.RETRY_QUEUE, Collections.singletonList(""));
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
        log.info("等待耗时:{}", System.currentTimeMillis() - now);
        log.info("当前剩余的key{}", commands.keys("*"));
        TimeUnit.SECONDS.sleep(3);
        Assertions.assertTrue(errConsumer, "任务错误次数耗尽需要走到消费者中");
    }


}