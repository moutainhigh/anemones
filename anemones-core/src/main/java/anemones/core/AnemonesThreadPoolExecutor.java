package anemones.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

class AnemonesThreadPoolExecutor extends ThreadPoolExecutor {
    private final Set<WorkerRunnable> workers = ConcurrentHashMap.newKeySet();

    AnemonesThreadPoolExecutor(int corePoolSize) {
        super(corePoolSize, corePoolSize, 0, TimeUnit.SECONDS, new SynchronousQueue<Runnable>() {
                    @Override
                    public boolean offer(Runnable o) {
                        try {
                            super.put(o);
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                        return true;
                    }
                },
                new ThreadFactory() {
                    private final AtomicInteger counter = new AtomicInteger(0);

                    @Override
                    public Thread newThread(Runnable r) {
                        Thread thread = new Thread(r);
                        thread.setName("Anemones-Worker-" + counter.getAndIncrement());
                        return thread;
                    }
                });
    }

    @Override
    protected void beforeExecute(Thread t, Runnable r) {
        workers.add((WorkerRunnable) r);

    }

    @Override
    protected void afterExecute(Runnable r, Throwable t) {
        workers.remove((WorkerRunnable) r);
    }


    public Collection<WorkerRunnable> getRunningWorkers() {
        return new ArrayList<>(workers);
    }
}
