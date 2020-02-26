package anemones.core;

import anemones.core.event.AnemonesCompleteEvent;
import anemones.core.event.AnemonesInboundEvent;
import anemones.core.event.AnemonesRetryEvent;
import anemones.core.event.AnemonesStartEvent;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

/**
 * 事件类是否正常
 */
public class EventTest {

    @Test
    void event() {
        List<AnemonesData> list = Collections.emptyList();
        String queue = "queue";
        String ns = "namespace";
        boolean rescue = true;
        AnemonesInboundEvent event = new AnemonesInboundEvent(list, queue, ns, rescue);
        Assertions.assertEquals(rescue, event.isRescue());
        Assertions.assertEquals(list, event.getParams());
        Assertions.assertEquals(queue, event.getQueue());
        Assertions.assertEquals(ns, event.getNamespace());

        AnemonesData d = new AnemonesData();
        d.setParam("1");
        d.setJobId("test");
        AnemonesRetryEvent retryEvent = new AnemonesRetryEvent(d);
        Assertions.assertEquals(d, retryEvent.getParam());
        Assertions.assertEquals(d, retryEvent.getPayload());

        AnemonesWorker w = new AnemonesWorker() {
            @Override
            public String queue() {
                return null;
            }

            @Override
            public Object perform(String param) {
                return null;
            }
        };
        Object result = 1;
        Throwable e = new RuntimeException();
        AnemonesCompleteEvent completeEvent = new AnemonesCompleteEvent(d, w, result, e);
        Assertions.assertEquals(result, completeEvent.getResult());
        Assertions.assertEquals(d, completeEvent.getParam());
        Assertions.assertEquals(w, completeEvent.getWorker());
        Assertions.assertEquals(e, completeEvent.getThrowable());

        AnemonesStartEvent startEvent = new AnemonesStartEvent(d);
        Assertions.assertEquals(d, startEvent.getParam());


    }
}
