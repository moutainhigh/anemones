package anemones.core;

import anemones.core.event.AnemonesEvent;
import anemones.core.event.AnemonesInboundEvent;
import anemones.core.event.AnemonesRetryEvent;
import io.lettuce.core.*;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.async.RedisAsyncCommands;
import io.lettuce.core.api.sync.RedisCommands;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

import static anemones.core.Constants.POLL_WAIT_RANDOM_TIME;
import static anemones.core.Constants.POLL_WAIT_TIME;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;

/**
 * 默认AnemonesManager
 *
 * @author hason
 */
@Slf4j
public class DefaultAnemonesManager implements AnemonesManager {
    private static final Map<String, AnemonesKeyCache> CACHE_MAP = new ConcurrentHashMap<>();

    /**
     * 任务阻塞拉取的超时时间
     */
    private static final int POLL_BLOCK_SECONDS = 3;
    private volatile boolean shutdown = false;
    private final String finalPrefix;
    private final String allPrefix;
    private final String namespace;
    private final Function<String, AnemonesKeyCache> cacheMapping;
    private final AnemonesParamConverter converter;
    private final Set<AnemonesEventListener> listeners = new TreeSet<>(Comparator.comparing(AnemonesEventListener::weight).reversed());
    private final List<AnemonesWorker> workers;
    private final int concurrency;
    private final int waitSecondsToTerminate;
    private final RedisClient redisClient;
    private final String pollerLockKey;

    private AnemonesThreadPoolExecutor executor;
    private Poller poller;
    private SchedulePoller schedulePoller;
    private StatefulRedisConnection<String, String> redis;

    public DefaultAnemonesManager(AnemonesConfig config) {
        if (config.getRedisUrl() == null) {
            throw new IllegalArgumentException("redisUrl不可为空");
        }
        this.finalPrefix = config.getFinalPrefix();
        this.allPrefix = config.getAllPrefix();
        this.namespace = config.getNamespace();
        this.cacheMapping = (q) -> new AnemonesKeyCache(finalPrefix, q);
        this.addListeners(config.getListeners());
        this.workers = config.getWorkers() == null ? Collections.emptyList() : new ArrayList<>(config.getWorkers());
        this.concurrency = config.getConcurrency();
        this.converter = config.getConverter();
        if (config.getWaitSecondsToTerminate() < 0) {
            config.setWaitSecondsToTerminate(0);
        }
        this.waitSecondsToTerminate = config.getWaitSecondsToTerminate();
        this.pollerLockKey = config.getPollerLockKey();
        this.redisClient = RedisClient.create(config.getRedisUrl());
    }


    @Override
    public List<String> submitTask(String queue, List<String> param) {
        return this.submitIn(queue, param, 0, null);
    }

    @Override
    public List<String> submitTask(String queue, List<String> param, Map<String, String> options) {
        return this.submitIn(queue, param, 0, null, options);
    }

    @Override
    public List<String> submitIn(String queue, List<String> param, int time, TimeUnit unit) {
        return this.submitIn(queue, param, time, unit, null);
    }

    @Override
    public List<String> submitIn(String queue, List<String> param, int timeUnit, TimeUnit unit, Map<String, String> options) {
        long currentTimeMillis = System.currentTimeMillis();
        List<String> jobIds = new ArrayList<>(param.size());
        List<AnemonesData> list = new ArrayList<>(param.size());
        for (String o : param) {
            AnemonesData datum = new AnemonesData();
            datum.setJobId(UUID.randomUUID().toString().replaceAll("-", ""));
            datum.setParam(o);
            datum.setQueue(queue);
            datum.setTimestamp(currentTimeMillis);
            datum.setOptions(options);
            long targetTimestamp;
            if (timeUnit > 0) {
                targetTimestamp = currentTimeMillis + unit.toMillis(timeUnit);
            } else {
                targetTimestamp = currentTimeMillis;
            }
            datum.setTargetTimestamp(targetTimestamp);
            list.add(datum);
            jobIds.add(datum.getJobId());
        }
        submitDirectly(queue, list, false);
        return jobIds;
    }

    @Override
    public void addListeners(Collection<AnemonesEventListener> listeners) {
        if (listeners != null && !listeners.isEmpty()) {
            this.listeners.addAll(listeners);
        }
    }

    @Override
    public void retryTask(AnemonesData param) {
        AnemonesKeyCache cache = getAnemonesKeyCache(param.getQueue());
        StatefulRedisConnection<String, String> conn = redisClient.connect();
        RedisAsyncCommands<String, String> commands = conn.async();
        commands.zadd(cache.getZsetKey(), param.getTargetTimestamp(), converter.serialize(param));
        fireEvent(new AnemonesRetryEvent(param));
    }

    /**
     * 提交任务到redis中
     *
     * @param queue  队列名
     * @param list   数据列表
     * @param rescue 本地提交是否为拯救数据(比如应用重启)
     */
    private void submitDirectly(String queue, List<AnemonesData> list, boolean rescue) {
        long now = System.currentTimeMillis();
        try {
            AnemonesKeyCache cache = getAnemonesKeyCache(queue);
            RedisAsyncCommands<String, String> commands = redis.async();
            RedisFuture[] futures = new RedisFuture[list.size() + 1];
            int index = 0;
            for (AnemonesData data : list) {
                if (data.getTargetTimestamp() > now) {
                    futures[index++] = commands.zadd(cache.getZsetKey(), data.getTargetTimestamp(), converter.serialize(data));
                } else if (rescue) {
                    futures[index++] = commands.rpush(cache.getListKey(), converter.serialize(data));
                } else {
                    futures[index++] = commands.lpush(cache.getListKey(), converter.serialize(data));
                }
            }
            futures[index++] = commands.zadd(allPrefix, now, queue);
            if (!LettuceFutures.awaitAll(10, TimeUnit.SECONDS, futures)) {
                throw new TimeoutException("redis命令超时");
            }
            /// 此处可以做过期处理
            //  conn.expire(allPrefix, EXPIRE_TIME);
            //  conn.expire(cache.getZsetKey(), EXPIRE_TIME);
            //  conn.expire(cache.getListKey(), EXPIRE_TIME);
        } catch (TimeoutException e) {
            throw new RuntimeException(e);
        }
        fireEvent(new AnemonesInboundEvent(list, queue, namespace, rescue));
    }

    @Override
    public void fireEvent(AnemonesEvent event) {
        for (AnemonesEventListener listener : listeners) {
            try {
                listener.notifyEvent(this, event);
                if (event.isPreventPopup()) {
                    return;
                }
            } catch (RuntimeException e) {
                log.error("[Anemones]严重,{}处理失败,param:{},listener:{}", event.getClass().getSimpleName(),
                        event.getPayload(),
                        listener.getClass().getName(), e);
            }
        }
    }

    @Override
    public boolean isShutdown() {
        return shutdown;
    }

    AnemonesKeyCache getAnemonesKeyCache(String queue) {
        return CACHE_MAP.computeIfAbsent(queue, cacheMapping);
    }

    @Override
    public void init() {
        if (this.converter == null) {
            throw new IllegalArgumentException("Converter不可为空");
        }
        this.redis = redisClient.connect();
        if (workers == null || workers.isEmpty()) {
            log.info("[Anemones]{}工作者数量为0,将不启动工作线程", namespace);
            return;
        }
        if (this.concurrency <= 0) {
            throw new IllegalArgumentException("并发数不可小于等于0");
        }
        this.executor = new AnemonesThreadPoolExecutor(this.concurrency);
        this.poller = new Poller();
        poller.start();
        this.schedulePoller = new SchedulePoller();
        schedulePoller.start();

    }

    @Override
    public void close() throws Exception {
        log.info("[Anemones] 开始关闭Anemones");
        shutdown = true;
        try {
            if (workers == null || workers.isEmpty()) {
                return;
            }
            this.poller.shutdown();
            this.schedulePoller.shutdown();
            // 防止获取到任务后还未提交到线程池 / 以及任务拉取安全结束
            this.poller.waitForShutdown();
            this.schedulePoller.waitForShutdown();
            if (this.executor.isShutdown()) {
                return;
            }
            this.executor.shutdown();
            if (!this.executor.awaitTermination(waitSecondsToTerminate, TimeUnit.SECONDS)) {
                this.executor.shutdownNow();
                Collection<WorkerRunnable> workers = this.executor.getRunningWorkers();
                Map<String, List<AnemonesData>> map = workers.stream().map(WorkerRunnable::getParam).collect(groupingBy(AnemonesData::getQueue, toList()));
                for (Map.Entry<String, List<AnemonesData>> entry : map.entrySet()) {
                    try {
                        submitDirectly(entry.getKey(), entry.getValue(), true);
                        log.warn("[Anemones]关闭Anemones, 重新推入 {} 的参数为:{}", entry.getKey(), entry.getValue());
                    } catch (RuntimeException e) {
                        log.error("[Anemones]关闭Anemones, 重新推入 {} 异常,任务丢失", entry.getKey(), e);
                    }
                }
            }
            log.info("[Anemones] Anemones关闭完成");
        } finally {
            closeRedis();
        }
    }


    private void closeRedis() {
        if (this.redis != null && this.redis.isOpen()) {
            try {
                redis.close();
            } catch (RuntimeException e) {
                log.error("[Anemones]Redis关闭失败", e);
            }
        }
        try {
            redisClient.shutdown();
        } catch (RuntimeException e) {
            log.error("[Anemones]Redis Client关闭失败", e);
        }
    }

    /**
     * 定时执行工作分发器
     * 将定时任务zset中的任务转移到工作list中
     */
    private class SchedulePoller extends Thread {

        private final SetArgs setArgs = SetArgs.Builder.ex(60).nx();
        private final StatefulRedisConnection<String, String> connection;
        private ThreadLocalRandom random = ThreadLocalRandom.current();

        private volatile boolean stop = false;

        SchedulePoller() {
            setName("Anemones-Schedule-Poller");
            this.connection = redisClient.connect();
        }

        @Override
        public void run() {
            try {
                RedisCommands<String, String> commands = connection.sync();
                while (!stop) {
                    try {
                        TimeUnit.SECONDS.sleep(POLL_WAIT_TIME + random.nextInt(POLL_WAIT_RANDOM_TIME));
                    } catch (InterruptedException e) {
                        continue;
                    }
                    try {
                        doInnerLoop(commands);
                    } catch (RedisCommandInterruptedException e) {
                        //
                    } catch (RuntimeException e) {
                        log.error("[Anemones] Anemones-Schedule-Poller 异常", e);
                    }
                }
                log.info("[Anemones] Anemones-Schedule-Poller 安全关闭...");

            } finally {
                try {
                    this.connection.close();
                } catch (Exception e) {
                    //
                }
            }

        }

        private void doInnerLoop(RedisCommands<String, String> commands) {
            if (!"OK".equals(commands.set(pollerLockKey, "0", setArgs))) {
                return;
            }
            long now = System.currentTimeMillis();
            for (AnemonesWorker worker : workers) {
                AnemonesKeyCache cache = getAnemonesKeyCache(worker.queue());
                List<String> params;
                while (!stop) {
                    params = commands.zrangebyscore(cache.getZsetKey(),
                            Range.create(0, now),
                            Limit.create(0, 100));
                    if (params != null && !params.isEmpty()) {
                        for (String param : params) {
                            if (commands.zrem(cache.getZsetKey(), param).equals(1L)) {
                                commands.rpush(cache.getListKey(), param);
                            }
                        }
                    } else {
                        break;
                    }
                }
                if (stop) {
                    break;
                }
            }
            commands.expire(pollerLockKey, 5);
        }

        public void shutdown() {
            this.stop = true;
            this.interrupt();
        }

        public void waitForShutdown() {
            while (this.connection.isOpen()) {
                try {
                    TimeUnit.MILLISECONDS.sleep(100);
                } catch (InterruptedException e) {
                    return;
                }
            }
        }
    }


    /**
     * 任务执行器
     * 获取任务队列中的任务并执行
     */
    private class Poller extends Thread {

        private final StatefulRedisConnection<String, String> connection;
        private volatile boolean stop = false;
        private ThreadLocalRandom random = ThreadLocalRandom.current();
        private final String[] WATCH_KEYS;
        private final Map<String, AnemonesWorker> REDIS_KEY_WORKER_MAP;

        public Poller() {
            setName("Anemones-Poller");
            workers.sort(Comparator.comparing(AnemonesWorker::weight).reversed());
            WATCH_KEYS = new String[workers.size()];
            REDIS_KEY_WORKER_MAP = new HashMap<>();
            for (int i = 0; i < workers.size(); i++) {
                AnemonesWorker worker = workers.get(i);
                String queue = worker.queue();
                String listKey = getAnemonesKeyCache(queue).getListKey();
                WATCH_KEYS[i] = listKey;
                REDIS_KEY_WORKER_MAP.put(listKey, worker);
            }
            this.connection = redisClient.connect();
        }

        @Override
        public void run() {
            try {
                RedisCommands<String, String> commands = connection.sync();
                while (!stop) {
                    try {
                        doInnerLoop(commands);
                    } catch (RedisCommandInterruptedException e) {
                        //
                    } catch (RuntimeException e) {
                        log.error("[Anemones] Anemones-Poller 异常", e);
                        try {
                            TimeUnit.SECONDS.sleep(1 + random.nextInt(POLL_WAIT_RANDOM_TIME));
                        } catch (InterruptedException ignored) {
                            //
                        }
                    }
                }
                log.info("[Anemones] Anemones-Poller 安全关闭...");
            } finally {
                try {
                    connection.close();
                } catch (RuntimeException e) {
                    //
                }
            }

        }

        private void doInnerLoop(RedisCommands<String, String> commands) {
            int availableProcessor = executor.getCorePoolSize() - executor.getActiveCount();
            if (availableProcessor <= 0) {
                try {
                    TimeUnit.MILLISECONDS.sleep(100);
                } catch (InterruptedException e) {
                    //
                }
                return;
            }

            while (!stop && availableProcessor > 0) {
                KeyValue<String, String> keyValue = commands.brpop(POLL_BLOCK_SECONDS, WATCH_KEYS);
                if (keyValue != null) {
                    String param = keyValue.getValue();
                    AnemonesData data;
                    try {
                        data = converter.deserialize(param);
                    } catch (RuntimeException e) {
                        log.error("[Anemones]严重,序列化失败,param:{}", param, e);
                        continue;
                    }

                    executor.execute(new WorkerRunnable(DefaultAnemonesManager.this,
                            REDIS_KEY_WORKER_MAP.get(keyValue.getKey()),
                            data));
                    availableProcessor--;
                } else {
                    break;
                }
            }
        }

        public void shutdown() {
            this.stop = true;
            this.interrupt();
        }

        public void waitForShutdown() {
            while (this.connection.isOpen()) {
                try {
                    TimeUnit.MILLISECONDS.sleep(100);
                } catch (InterruptedException e) {
                    return;
                }
            }
        }
    }

}
