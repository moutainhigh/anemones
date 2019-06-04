package anemones.core.extension;

import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import redis.embedded.RedisServer;

public class EmbeddedRedisExtension implements BeforeAllCallback, AfterAllCallback {
    public static final int PORT = 16379;
    public static final RedisURI REDIS_URI = RedisURI.create("localhost", PORT);
    private static RedisServer REDIS_SERVER;
    private static RedisClient REDIS_CLIENT;
    public static StatefulRedisConnection<String, String> REDIS_CONN;

    @Override
    public void beforeAll(ExtensionContext context) throws Exception {
        REDIS_SERVER = RedisServer.builder()
                .port(PORT)
                .setting("daemonize no")
                .setting("appendonly no")
                .setting("bind 127.0.0.1")
                .build();
        REDIS_SERVER.start();
        REDIS_CLIENT = RedisClient.create(REDIS_URI);
        REDIS_CONN = REDIS_CLIENT.connect();
    }

    @Override
    public void afterAll(ExtensionContext extensionContext) throws Exception {
        try {
            REDIS_CONN.close();
        } catch (RuntimeException e) {
            //
        }
        try {
            REDIS_CLIENT.shutdown();
        } catch (RuntimeException e) {
            //
        }
        try {
            REDIS_SERVER.stop();
        } catch (RuntimeException e) {
            //
        }
    }
}