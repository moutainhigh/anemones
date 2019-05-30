package anemones.core.event;

import anemones.core.AnemonesData;
import anemones.core.AnemonesWorker;

public class AnemonesCompleteEvent extends AnemonesEvent {
    private final AnemonesData param;
    private final AnemonesWorker worker;
    private final Throwable throwable;

    public AnemonesCompleteEvent(AnemonesData param, AnemonesWorker worker, Throwable throwable) {
        this.param = param;
        this.worker = worker;
        this.throwable = throwable;
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
