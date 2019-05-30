package anemones.core;

import anemones.core.event.AnemonesEvent;

/**
 * Anemones事件监听器
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

}
