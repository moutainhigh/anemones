package anemones.core.support;

import anemones.core.AnemonesWorker;

public class RetryTask implements AnemonesWorker {

    public static final String RETRY_QUEUE = "retry";

    @Override
    public int retry() {
        return 1;
    }

    @Override
    public String queue() {
        return RETRY_QUEUE;
    }

    @Override
    public void perform(String param) {
        throw new IllegalStateException("失败");
    }
}
