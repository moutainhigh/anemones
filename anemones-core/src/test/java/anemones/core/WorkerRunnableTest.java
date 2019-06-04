package anemones.core;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class WorkerRunnableTest {


    @Test
    void equalsAndHashCode() {
        String jobId = "test";
        AnemonesData d1 = new AnemonesData();
        d1.setJobId(jobId);

        WorkerRunnable r1 = new WorkerRunnable(Mockito.mock(AnemonesManager.class), new AnemonesWorker() {
            @Override
            public String queue() {
                return null;
            }

            @Override
            public Object perform(String param) {
                return null;
            }
        }, d1);

        AnemonesData d2 = new AnemonesData();
        d2.setJobId(jobId);
        WorkerRunnable r2 = new WorkerRunnable(Mockito.mock(AnemonesManager.class), new AnemonesWorker() {
            @Override
            public String queue() {
                return "asd";
            }

            @Override
            public Object perform(String param) {
                return null;
            }
        }, d2);
        Assertions.assertEquals(r1, r2);
        Assertions.assertEquals(r1.hashCode(), r2.hashCode());
    }

}