package anemones.core;

import anemones.core.event.AnemonesEvent;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Anemones任务提交管理器
 *
 * @author hason
 */
public interface AnemonesManager extends AutoCloseable {

    /**
     * 批量提交任务
     *
     * @param queue 队列名
     * @param param 多个任务 的 参数
     * @return job id 列表
     */
    List<String> submitTask(String queue, List<String> param);

    /**
     * 批量提交任务
     *
     * @param queue   队列名
     * @param param   多个任务 的 参数
     * @param options 其他选项,一般用于中间件使用
     * @return job id 列表
     */
    List<String> submitTask(String queue, List<String> param, Map<String, String> options);

    /**
     * 批量提交任务在指定时间执行
     *
     * @param queue 任务
     * @param param 参数列表
     * @param time  时间数额
     * @param unit  时间单位
     * @return job id 列表
     */
    List<String> submitIn(String queue, List<String> param, int time, TimeUnit unit);

    /**
     * 批量提交任务在指定时间执行
     *
     * @param queue    任务
     * @param param    参数列表
     * @param timeUnit 时间数额
     * @param unit     时间单位
     * @param options  其他选项,一般用于中间件使用
     * @return job id 列表
     */
    List<String> submitIn(String queue, List<String> param, int timeUnit, TimeUnit unit, Map<String, String> options);


    /**
     * 设置监听器
     * 此方法并非线程安全,请不要在多线程情况下调用
     *
     * @param listeners 监听器集合
     */
    void addListeners(Collection<AnemonesEventListener> listeners);

    /**
     * 任务进行重试
     *
     * @param param 参数
     */
    void retryTask(AnemonesData param);

    /**
     * 触发事件
     *
     * @param event 任务实例
     */
    void fireEvent(AnemonesEvent event);

    /**
     * 初始化AnemonesManager
     */
    void init();

    /**
     * 是否已关闭
     *
     * @return 关闭则返回true
     */
    boolean isShutdown();
}
