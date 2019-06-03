package anemones.core.support;

import anemones.core.AnemonesWorker;
import anemones.core.DefaultAnemonesManagerTest;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.TimeUnit;

@Slf4j
public abstract class TestWork implements AnemonesWorker {

    @Override
    @SneakyThrows
    public Object perform(String param) {
        String[] parts = param.split("_");
        String name = parts[0];
        DefaultAnemonesManagerTest.STARTED_JOB.put(name, System.currentTimeMillis());
        String sleepTime = parts[1];
        log.info("开始执行{} : {}, 执行时间: {}s", queue(), name, sleepTime);
        int waitSeconds = Integer.parseInt(sleepTime);
        if (waitSeconds != 0) {
            TimeUnit.SECONDS.sleep(waitSeconds);
        }
        return null;
    }

}
