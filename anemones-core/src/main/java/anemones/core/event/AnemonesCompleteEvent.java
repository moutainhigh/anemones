package anemones.core.event;

import anemones.core.AnemonesData;
import anemones.core.AnemonesWorker;

public class AnemonesCompleteEvent extends AnemonesEvent {
    private final AnemonesData param;
    private final AnemonesWorker worker;
    private final Object result;
    private final Throwable throwable;

    public AnemonesCompleteEvent(AnemonesData param, AnemonesWorker worker, Object result, Throwable throwable) {
        this.param = param;
        this.worker = worker;
        this.result = result;
        this.throwable = throwable;
    }

    public Object getResult() {
        return result;
    }

    public AnemonesWorker getWorker() {
        return worker;
    }

    public Throwable getThrowable() {
        return throwable;
    }

    public AnemonesData getParam() {
        return param;
    }

    @Override
    public Object getPayload() {
        return getParam();
    }
}
