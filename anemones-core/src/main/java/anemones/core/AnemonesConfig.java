package anemones.core;


import io.lettuce.core.RedisURI;

import java.util.Collection;

public class AnemonesConfig {

    private static final String COMMON_PREFIX = "sidekiq";
    private String namespace;
    private AnemonesParamConverter converter;
    private RedisURI redisUrl;
    private Collection<AnemonesEventListener> listeners;
    private Collection<AnemonesWorker> workers;
    private int concurrency;
    private int waitSecondsToTerminate = 10;

    public int getConcurrency() {
        return concurrency;
    }

    /**
     * 设置进程执行任务的并发数
     *
     * @param concurrency 并发数
     */
    public void setConcurrency(int concurrency) {
        this.concurrency = concurrency;
    }

    /**
     * 设置自定义命名空间
     *
     * @param namespace 自定义命名空间,请使用纯字母
     */
    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    public String getNamespace() {
        return namespace;
    }

    public String getAllPrefix() {
        return getPrefix() + "_all";
    }

    public String getFinalPrefix() {
        return getPrefix() + ":";
    }

    public String getPollerLockKey() {
        return getPrefix() + "_poller";
    }

    private String getPrefix() {
        return this.namespace != null ? COMMON_PREFIX + "_" + namespace : COMMON_PREFIX;
    }


    /**
     * @param converter The {@link AnemonesParamConverter} instance to be used convert param to {@link String}
     *                  or convert {@link String} to param.
     */
    public void setConverter(AnemonesParamConverter converter) {
        this.converter = converter;
    }


    public AnemonesParamConverter getConverter() {
        return converter;
    }

    public Collection<AnemonesEventListener> getListeners() {
        return listeners;
    }

    public void setListeners(Collection<AnemonesEventListener> listeners) {
        this.listeners = listeners;
    }

    public Collection<AnemonesWorker> getWorkers() {
        return workers;
    }

    public void setWorkers(Collection<AnemonesWorker> workers) {
        this.workers = workers;
    }

    public int getWaitSecondsToTerminate() {
        return waitSecondsToTerminate;
    }

    /**
     * 设置等待任务结束的时间, 应用终止时将会等待
     *
     * @param waitSecondsToTerminate 秒
     */
    public void setWaitSecondsToTerminate(int waitSecondsToTerminate) {
        this.waitSecondsToTerminate = waitSecondsToTerminate;
    }

    public RedisURI getRedisUrl() {
        return redisUrl;
    }

    /**
     * 设置redis的连接地址, 参见 {@link RedisURI}
     *
     * @param redisUrl redis地址
     */
    public void setRedisUrl(RedisURI redisUrl) {
        this.redisUrl = redisUrl;
    }

}
