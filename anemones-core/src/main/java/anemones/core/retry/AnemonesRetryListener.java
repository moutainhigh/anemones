package anemones.core.retry;

import anemones.core.*;
import anemones.core.event.AnemonesCompleteEvent;
import anemones.core.event.AnemonesEvent;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BiConsumer;

@Slf4j
public class AnemonesRetryListener implements AnemonesEventListener {

    private final BiConsumer<AnemonesData, Throwable> errorConsumer;

    public AnemonesRetryListener(BiConsumer<AnemonesData, Throwable> errorConsumer) {
        this.errorConsumer = errorConsumer;
    }

    @Override
    public void notifyEvent(AnemonesManager manager, AnemonesEvent event) {
        if (!(event instanceof AnemonesCompleteEvent)) {
            return;
        }
        AnemonesCompleteEvent e = (AnemonesCompleteEvent) event;
        Throwable throwable = e.getThrowable();
        if (throwable == null) {
            return;
        }
        AnemonesWorker worker = e.getWorker();
        AnemonesData param = e.getParam();
        if (!(throwable instanceof AnemonesAbandonException)) {
            int maxRetry = worker.retry() > 5 ? 5 : worker.retry();
            if (param.getRetry() < maxRetry) {
                event.preventPopup();
                if (manager.isShutdown()) {
                    log.warn("[Anemones-Retry]任务执行异常失败,但manager已关闭,等待任务救援,param:{}", param);
                    return;
                }
                param.setRetry(param.getRetry() + 1);
                param.setMsg(throwable.getClass().getName() + " : " + throwable.getMessage());
                param.setTargetTimestamp(System.currentTimeMillis() + retryForDelay(param.getRetry()));
                manager.retryTask(param);
                log.warn("[Anemones-Retry]任务执行异常失败,即将重试,param:{}", param);
            } else if (worker.retry() != 0) {
                log.error("[Anemones-Retry]任务重试次数耗尽,任务死亡,param:{}", param);
                if (errorConsumer != null) {
                    errorConsumer.accept(param, throwable);
                }
            } else {
                log.warn("[Anemones-Retry]任务失败,但不需要重试,任务死亡,param:{}", param);
            }
        }
    }

    @Override
    public int weight() {
        return Integer.MAX_VALUE;
    }

    private long retryForDelay(int count) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        int num = count * count * count * count + 15 + (random.nextInt(30) * count);
        return num * 1000;
    }
}
