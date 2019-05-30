package anemones.core.event;

import anemones.core.AnemonesData;

public class AnemonesRetryEvent extends AnemonesEvent {

    private final AnemonesData param;

    public AnemonesRetryEvent(AnemonesData param) {
        this.param = param;
    }

    public AnemonesData getParam() {
        return param;
    }

    @Override
    public Object getPayload() {
        return getParam();
    }
}
