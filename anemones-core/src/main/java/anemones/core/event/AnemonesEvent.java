package anemones.core.event;

public abstract class AnemonesEvent {

    private boolean preventPopup = false;

    /**
     * 获取事件携带物，一般用于日志记录
     *
     * @return 携带对象
     */
    public abstract Object getPayload();


    /**
     * 是否阻止事件继续冒泡
     *
     * @return 是则返回true
     */
    public boolean isPreventPopup() {
        return preventPopup;
    }

    /**
     * 设置阻止事件继续传播
     * 比如当任务状态已被此监听器改变时使用
     */
    public void preventPopup() {
        preventPopup = true;
    }
}
