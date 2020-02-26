package anemones.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.AbstractQueuedSynchronizer;

/**
 * 记录正在的执行的线程池
 * 无等待执行任务
 */
class AnemonesThreadPoolExecutor extends ThreadPoolExecutor {
    private final Set<WorkerRunnable> workers = ConcurrentHashMap.newKeySet();
    private final Waiter waiter;

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
        this.waiter = new Waiter(corePoolSize);
    }

    @Override
    protected void beforeExecute(Thread t, Runnable r) {
        workers.add((WorkerRunnable) r);
        waiter.acquire();

    }

    @Override
    protected void afterExecute(Runnable r, Throwable t) {
        workers.remove((WorkerRunnable) r);
        waiter.release();
    }

    public int waitForAvailableProcessor() throws InterruptedException {
        return waiter.waitForRelease();
    }

    public Collection<WorkerRunnable> getRunningWorkers() {
        return new ArrayList<>(workers);
    }

    static class Waiter {

        private final Sync sync;

        Waiter(int total) {
            this.sync = new Sync(total);
        }

        public void acquire() {
            sync.acquireShared(1);
        }


        public void release() {
            sync.releaseShared(1);
        }

        public int waitForRelease() throws InterruptedException {
            return sync.waitForRelease();
        }

        private static class Sync extends AbstractQueuedSynchronizer {

            Sync(int total) {
                setState(total);
            }

            @Override
            protected int tryAcquireShared(int acquires) {
                if (acquires == 1) {
                    while (true) {
                        int available = getState();
                        int remaining = available - acquires;
                        if (remaining < 0 ||
                                compareAndSetState(available, remaining))
                            return remaining;
                    }
                } else {
                    // 可能出现已经release结果才刚刚等待的情况
                    int available = getState();
                    return available > 0 ? available : -1;
                }
            }

            @Override
            protected boolean tryReleaseShared(int releases) {
                while (true) {
                    int current = getState();
                    int next = current + releases;
                    if (next < current) // overflow
                        throw new Error("Maximum permit count exceeded");
                    if (compareAndSetState(current, next))
                        return true;
                }
            }

            public int waitForRelease() throws InterruptedException {
                int result = getState();
                if (result > 0) {
                    return result;
                }
                acquireSharedInterruptibly(2);
                return getState();
            }
        }

    }


}
