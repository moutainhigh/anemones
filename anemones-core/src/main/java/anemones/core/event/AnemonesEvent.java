package anemones.core.event;

public abstract class AnemonesEvent {

    /**
     * 获取事件携带物，一般用于日志记录
     *
     * @return 携带对象
     */
    public abstract Object getPayload();
}
