package anemones.core.event;

import anemones.core.AnemonesData;

import java.util.List;

public class AnemonesInboundEvent extends AnemonesEvent {
    private final String queue;
    private final String namespace;
    private final boolean rescue;
    private final List<AnemonesData> params;

    public AnemonesInboundEvent(List<AnemonesData> params,
                                String queue,
                                String namespace,
                                boolean rescue) {
        this.params = params;
        this.queue = queue;
        this.namespace = namespace;
        this.rescue = rescue;
    }

    public boolean isRescue() {
        return rescue;
    }

    public String getNamespace() {
        return namespace;
    }

    public String getQueue() {
        return queue;
    }

    public List<AnemonesData> getParams() {
        return params;
    }


    @Override
    public Object getPayload() {
        return getParams();
    }
}
