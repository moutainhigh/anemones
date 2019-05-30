package anemones.core.event;

import anemones.core.AnemonesData;

public class AnemonesStartEvent extends AnemonesEvent {
    private final AnemonesData param;


    public AnemonesStartEvent(AnemonesData param) {
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
