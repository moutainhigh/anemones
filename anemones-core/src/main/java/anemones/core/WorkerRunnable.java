package anemones.core;

import anemones.core.event.AnemonesCompleteEvent;
import anemones.core.event.AnemonesStartEvent;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;

/**
 * Anemones工作实现类
 *
 * @author hason
 */
@Slf4j
@EqualsAndHashCode(of = {"param"})
class WorkerRunnable implements Runnable {

    private final AnemonesWorker worker;
    private final AnemonesData param;
    private final AnemonesManager manager;


    public WorkerRunnable(AnemonesManager manager,
                          AnemonesWorker worker,
                          AnemonesData param) {
        this.manager = manager;
        this.worker = worker;
        this.param = param;
    }

    public AnemonesData getParam() {
        return param;
    }

    @Override
    public void run() {
        manager.fireEvent(new AnemonesStartEvent(param));
        Throwable throwable = null;
        Object result = null;
        try {
            result = this.worker.perform(param.getParam());
        } catch (Throwable e) {
            throwable = e;
            if (!(e instanceof AnemonesAbandonException)) {
                log.error("[Anemones]任务执行失败,param:{}", param, throwable);
            }
        }
        manager.fireEvent(new AnemonesCompleteEvent(param, worker, result, throwable));
    }

}
