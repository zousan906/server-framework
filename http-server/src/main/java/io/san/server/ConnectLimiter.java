package io.san.server;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;

public class ConnectLimiter {
    private Semaphore semaphore;
    private final int limit;
    private final LongAdder connectAdder;
    private final LongAdder refusedAdder;

    public ConnectLimiter(int limit) {
        this.limit = limit;
        this.connectAdder = new LongAdder();
        this.refusedAdder = new LongAdder();
        if (this.limit > 0) {
            this.semaphore = new Semaphore(limit);
        }
    }


    public long getCount() {
        return connectAdder.sum();
    }

    public long getLimit() {
        return limit;
    }


    public boolean tryAcquire(int timeout, TimeUnit unit) {
        this.connectAdder.increment();
        if (this.limit <= 0) return true;
        try {
            boolean permit = this.semaphore.tryAcquire(1, timeout, unit);
            if (!permit) {
                this.connectAdder.decrement();
                this.refusedAdder.increment();
            }
            return permit;
        } catch (InterruptedException e) {
            this.connectAdder.decrement();
        }
        return false;
    }

    public void release() {
        this.connectAdder.decrement();
        if (this.limit > 0) {
            this.semaphore.release();
        }
    }
}
