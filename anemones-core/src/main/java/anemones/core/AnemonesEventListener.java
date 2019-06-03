package anemones.core;

import anemones.core.event.AnemonesEvent;

/**
 * Anemones事件记录者
 *
 * @author hason
 */
@FunctionalInterface
public interface AnemonesEventListener {

    /**
     * 通知事件发生
     *
     * @param manager 管理员
     * @param event   事件
     */
    void notifyEvent(AnemonesManager manager, AnemonesEvent event);


    /**
     * 监听器的权重,权重越高处理越优先
     *
     * @return 权重值
     */
    default int weight() {
        return 1;
    }
}
