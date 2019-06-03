package anemones.core;

public interface AnemonesWorker {

    /**
     * 队列名称
     *
     * @return 队列名称
     */
    String queue();

    /**
     * 执行任务
     *
     * @param param 任务参数
     * @return 任务结果
     */
    Object perform(String param);

    /**
     * 执行任务的权重,权重越大则越先执行
     *
     * @return 权重
     */
    default int weight() {
        return 1;
    }

    /**
     * 最大重试次数, 如果为0则不重试
     *
     * @return 最大重试次数
     */
    default int retry() {
        return 0;
    }
}
