package anemones.core;

import lombok.Data;

import java.util.Map;

/**
 * Anemones数据
 *
 * @author hason
 */
@Data
public class AnemonesData {
    /**
     * 队列
     */
    private String queue;
    /**
     * 数据产生时间戳
     */
    private long timestamp;
    /**
     * 目标执行时间戳
     */
    private long targetTimestamp;
    /**
     * 参数
     */
    private String param;
    /**
     * 任务id
     */
    private String jobId;

    /**
     * 当前重试次数
     */
    private int retry;

    /**
     * 携带信息
     */
    private String msg;

    /**
     * 其他参数,用于中间件使用
     */
    private Map<String, String> options;


}
